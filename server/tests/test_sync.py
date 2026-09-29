import pytest

from app import main


def change(id_, name="Молоко", table="product", updated_at=1, deleted=False, **extra):
    return {
        "table": table, "id": id_, "data": {"name": name, "kcal": 60, **extra},
        "updated_at": updated_at, "deleted": deleted,
    }


def sync(client, device, cursor=0, changes=()):
    response = client.post("/sync", json={"device_id": device, "cursor": cursor, "changes": list(changes)})
    assert response.status_code == 200, response.text
    return response.json()


def test_изменения_первого_телефона_приходят_второму(client):
    a = sync(client, "A", changes=[change("p1"), change("p2", name="Хлеб")])
    assert a["changes"] == [] or all(c["device_id"] == "A" for c in a["changes"])

    b = sync(client, "B", cursor=0)
    assert {c["id"] for c in b["changes"]} == {"p1", "p2"}
    milk = next(c for c in b["changes"] if c["id"] == "p1")
    assert milk["data"] == {"name": "Молоко", "kcal": 60}
    assert milk["table"] == "product"
    assert milk["device_id"] == "A"
    assert b["has_more"] is False


def test_курсор_растёт_и_повтор_ничего_не_даёт(client):
    first = sync(client, "A", changes=[change("p1")])
    b1 = sync(client, "B")
    assert b1["cursor"] >= 1
    b2 = sync(client, "B", cursor=b1["cursor"])
    assert b2["changes"] == []
    assert b2["cursor"] == b1["cursor"]

    sync(client, "A", cursor=first["cursor"], changes=[change("p3")])
    b3 = sync(client, "B", cursor=b2["cursor"])
    assert [c["id"] for c in b3["changes"]] == ["p3"]
    assert b3["cursor"] > b2["cursor"]


def test_свои_изменения_не_возвращаются(client):
    a1 = sync(client, "A", changes=[change("p1")])
    assert a1["changes"] == []
    a2 = sync(client, "A", cursor=a1["cursor"])
    assert a2["changes"] == []


def test_побеждает_пришедшее_позже(client):
    sync(client, "A", changes=[change("p1", name="Молоко A", updated_at=500)])
    # У телефона B часы отстают: updated_at меньше, но пришло позже — побеждает B
    b = sync(client, "B", changes=[change("p1", name="Молоко B", updated_at=100)])
    a = sync(client, "A", cursor=1)
    assert [c["data"]["name"] for c in a["changes"]] == ["Молоко B"]
    # Оба в итоге с одинаковыми данными: у B своя версия, A получил её
    full = sync(client, "C", cursor=0)
    assert [c["data"]["name"] for c in full["changes"]] == ["Молоко B"]
    assert b["cursor"] >= 2


def test_мягкое_удаление_доходит(client):
    sync(client, "A", changes=[change("p1")])
    b = sync(client, "B")
    sync(client, "A", cursor=1, changes=[change("p1", deleted=True, updated_at=2)])
    b2 = sync(client, "B", cursor=b["cursor"])
    assert b2["changes"][0]["deleted"] is True
    assert b2["changes"][0]["id"] == "p1"


def test_первая_отправка_с_нуля_не_возвращает_присланное(client):
    """Новый телефон отправляет свои данные с курсором 0 и получает только чужие."""
    sync(client, "B", changes=[change("b1")])
    a = sync(client, "A", cursor=0, changes=[change("a1"), change("a2")])
    assert [c["id"] for c in a["changes"]] == ["b1"]
    assert sync(client, "A", cursor=a["cursor"])["changes"] == []


def test_полная_загрузка_отдаёт_и_свои_записи(client):
    """После очистки базы (ТЗ 8.4) телефон просит всё с нуля — свои записи тоже нужны."""
    sync(client, "A", changes=[change("p1")])
    full = sync(client, "A", cursor=0)
    assert [c["id"] for c in full["changes"]] == ["p1"]


def test_неизвестные_поля_сохраняются_как_есть(client):
    """Сервер не знает схему: новое поле с нового телефона дойдёт до других без изменений сервера."""
    sync(client, "A", changes=[change("p1", micro={"vit_c": 12.5}, new_field=[1, 2])])
    data = sync(client, "B")["changes"][0]["data"]
    assert data["micro"] == {"vit_c": 12.5}
    assert data["new_field"] == [1, 2]


def test_разные_таблицы_с_одинаковым_id_не_путаются(client):
    sync(client, "A", changes=[change("x", table="product"), change("x", table="pan", name="Кастрюля")])
    got = {(c["table"], c["data"]["name"]) for c in sync(client, "B")["changes"]}
    assert got == {("product", "Молоко"), ("pan", "Кастрюля")}


def test_порции(client, monkeypatch):
    monkeypatch.setattr(main, "PAGE_SIZE", 1000)
    sync(client, "A", changes=[change(f"p{i}") for i in range(2500)])
    cursor, seen, pages = 0, [], 0
    while True:
        page = sync(client, "B", cursor=cursor)
        seen += [c["id"] for c in page["changes"]]
        cursor = page["cursor"]
        pages += 1
        if not page["has_more"]:
            break
    assert pages == 3
    assert len(seen) == 2500
    assert len(set(seen)) == 2500
    assert sync(client, "B", cursor=cursor)["changes"] == []


def test_неизвестная_таблица_400_и_ничего_не_записано(client):
    response = client.post(
        "/sync",
        json={"device_id": "A", "cursor": 0, "changes": [change("p1"), change("x", table="users")]},
    )
    assert response.status_code == 400
    assert "users" in response.json()["detail"]
    assert client.get("/health").json()["records"] == 0


@pytest.mark.parametrize("body", [
    {"device_id": "", "cursor": 0, "changes": []},
    {"device_id": "A", "cursor": -1, "changes": []},
    {"device_id": "A", "cursor": 0, "changes": [{"table": "product", "id": "p", "data": "не объект",
                                                  "updated_at": 1, "deleted": False}]},
])
def test_неверный_запрос_422(client, body):
    assert client.post("/sync", json=body).status_code == 422


def test_данные_переживают_перезапуск(settings):
    from fastapi.testclient import TestClient
    from tests.conftest import KEY
    first = TestClient(main.create_app(settings), headers={"X-Family-Key": KEY})
    sync(first, "A", changes=[change("p1")])
    second = TestClient(main.create_app(settings), headers={"X-Family-Key": KEY})
    assert [c["id"] for c in sync(second, "B")["changes"]] == ["p1"]
