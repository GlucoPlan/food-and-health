from app import main

JPEG = b"\xff\xd8\xff\xe0" + b"\x00" * 100 + b"\xff\xd9"
PHOTO = "3f2a9c1e-7b4d-4e8a-9f10-2c3d4e5f6a7b"


def test_загрузка_под_id_телефона_и_скачивание(client, settings):
    response = client.put(f"/photos/{PHOTO}", content=JPEG, headers={"Content-Type": "image/jpeg"})
    assert response.status_code == 200
    assert response.json() == {"id": PHOTO}
    assert (settings.photos_dir / f"{PHOTO}.jpg").read_bytes() == JPEG

    got = client.get(f"/photos/{PHOTO}")
    assert got.status_code == 200
    assert got.headers["content-type"] == "image/jpeg"
    assert got.content == JPEG


def test_повторная_загрузка_безопасна(client, settings):
    assert client.put(f"/photos/{PHOTO}", content=JPEG).status_code == 200
    other = JPEG + b"\x00"
    assert client.put(f"/photos/{PHOTO}", content=other).status_code == 200
    # Фото неизменяемы: первое не перезаписывается
    assert (settings.photos_dir / f"{PHOTO}.jpg").read_bytes() == JPEG
    assert not list(settings.photos_dir.glob("*.part"))


def test_кривой_id_при_загрузке_422(client):
    for bad in ["abc", "0" * 32, PHOTO.upper(), "..%2F..%2Fetc%2Fpasswd"]:
        assert client.put(f"/photos/{bad}", content=JPEG).status_code in (404, 422), bad


def test_не_jpeg_отклоняется(client):
    assert client.put(f"/photos/{PHOTO}", content=b"\x89PNG\r\n\x1a\n...").status_code == 415
    assert client.put(f"/photos/{PHOTO}", content=b"").status_code == 415


def test_слишком_большое_отклоняется(client, monkeypatch, settings):
    monkeypatch.setattr(main, "MAX_PHOTO_BYTES", 1000)
    assert client.put(f"/photos/{PHOTO}", content=JPEG + b"\x00" * 2000).status_code == 413
    assert not (settings.photos_dir / f"{PHOTO}.jpg").exists()


def test_несуществующее_и_кривое_id_404(client):
    assert client.get(f"/photos/{PHOTO}").status_code == 404
    assert client.get("/photos/..%2F..%2Fetc%2Fpasswd").status_code == 404
    assert client.get("/photos/ABC").status_code == 404


def test_старый_post_больше_не_работает(client):
    assert client.post("/photos", content=JPEG).status_code in (404, 405)
