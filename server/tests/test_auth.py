import pytest

from app import config

ENDPOINTS = [
    ("get", "/health", None),
    ("post", "/sync", {"device_id": "a", "cursor": 0, "changes": []}),
    ("put", "/photos/3f2a9c1e-7b4d-4e8a-9f10-2c3d4e5f6a7b", None),
    ("get", "/photos/3f2a9c1e-7b4d-4e8a-9f10-2c3d4e5f6a7b", None),
]


@pytest.mark.parametrize("method,path,body", ENDPOINTS)
def test_без_ключа_401(anonymous, method, path, body):
    response = getattr(anonymous, method)(path, **({"json": body} if body else {}))
    assert response.status_code == 401


@pytest.mark.parametrize("method,path,body", ENDPOINTS)
def test_неверный_ключ_401(anonymous, method, path, body):
    kwargs = {"headers": {"X-Family-Key": "wrong-key-wrong-key"}}
    if body:
        kwargs["json"] = body
    assert getattr(anonymous, method)(path, **kwargs).status_code == 401


def test_с_ключом_health(client):
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "records": 0}


def test_короткий_ключ_не_принимается(monkeypatch):
    monkeypatch.setenv("FH_FAMILY_KEY", "short")
    with pytest.raises(RuntimeError):
        config.from_env()


def test_настройки_из_окружения(monkeypatch, tmp_path):
    monkeypatch.setenv("FH_FAMILY_KEY", "k" * 32)
    monkeypatch.setenv("FH_DATA_DIR", str(tmp_path))
    s = config.from_env()
    assert s.db_path == tmp_path / "fh.db"
    assert s.photos_dir == tmp_path / "photos"
