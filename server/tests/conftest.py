import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app

KEY = "test-family-key-0123456789"


@pytest.fixture
def settings(tmp_path):
    return Settings(family_key=KEY, data_dir=tmp_path / "data")


@pytest.fixture
def client(settings):
    return TestClient(create_app(settings), headers={"X-Family-Key": KEY})


@pytest.fixture
def anonymous(settings):
    return TestClient(create_app(settings))
