from app import main

JPEG = b"\xff\xd8\xff\xe0" + b"\x00" * 100 + b"\xff\xd9"


def test_загрузка_и_скачивание(client, settings):
    response = client.post("/photos", content=JPEG, headers={"Content-Type": "image/jpeg"})
    assert response.status_code == 200
    photo_id = response.json()["id"]
    assert len(photo_id) == 32
    assert (settings.photos_dir / f"{photo_id}.jpg").read_bytes() == JPEG

    got = client.get(f"/photos/{photo_id}")
    assert got.status_code == 200
    assert got.headers["content-type"] == "image/jpeg"
    assert got.content == JPEG


def test_не_jpeg_отклоняется(client):
    assert client.post("/photos", content=b"\x89PNG\r\n\x1a\n...").status_code == 415
    assert client.post("/photos", content=b"").status_code == 415


def test_слишком_большое_отклоняется(client, monkeypatch):
    monkeypatch.setattr(main, "MAX_PHOTO_BYTES", 1000)
    assert client.post("/photos", content=JPEG + b"\x00" * 2000).status_code == 413


def test_несуществующее_и_кривое_id_404(client):
    assert client.get("/photos/" + "a" * 32).status_code == 404
    assert client.get("/photos/..%2F..%2Fetc%2Fpasswd").status_code == 404
    assert client.get("/photos/ABC").status_code == 404
