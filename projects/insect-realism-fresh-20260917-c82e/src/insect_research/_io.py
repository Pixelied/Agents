"""Small shared primitives for atomic, cooperative metadata writes."""
from contextlib import contextmanager
from collections.abc import Iterator
import os
from pathlib import Path
import tempfile
from typing import TextIO


@contextmanager
def exclusive_lock(path: Path) -> Iterator[None]:
    """Fail closed if another writer owns this file. Never steal stale locks."""
    path.parent.mkdir(parents=True, exist_ok=True)
    lock = path.with_name(path.name + ".lock")
    try:
        fd = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    except FileExistsError as exc:
        raise RuntimeError(f"metadata lock exists: {lock}; check its owner before removing it") from exc
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            handle.write(f"pid={os.getpid()}\n")
        yield
    finally:
        lock.unlink(missing_ok=True)


@contextmanager
def atomic_text_writer(path: Path) -> Iterator[TextIO]:
    """Replace a complete UTF-8 file only after the write and fsync succeed."""
    if path.is_symlink():
        raise ValueError(f"metadata target must not be a symlink: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    handle = tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", newline="",
                                         dir=path.parent, prefix=f".{path.name}.", suffix=".tmp", delete=False)
    temporary = Path(handle.name)
    try:
        with handle:
            yield handle
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)
