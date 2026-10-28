"""Minimal NBT writer for structure templates (gzip, big-endian, Java edition)."""
import gzip
import struct


class Byte(int):
    pass


class Short(int):
    pass


class Long(int):
    pass


class Float(float):
    pass


class Double(float):
    pass


class IntList(list):
    """Written as TAG_List of TAG_Int (structure positions use lists, not int arrays)."""


class DoubleList(list):
    pass


class FloatList(list):
    pass


TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE = 0, 1, 2, 3, 4, 5, 6
TAG_STRING, TAG_LIST, TAG_COMPOUND = 8, 9, 10


def _tag_type(v):
    if isinstance(v, bool):
        return TAG_BYTE
    if isinstance(v, Byte):
        return TAG_BYTE
    if isinstance(v, Short):
        return TAG_SHORT
    if isinstance(v, Long):
        return TAG_LONG
    if isinstance(v, int):
        return TAG_INT
    if isinstance(v, Float):
        return TAG_FLOAT
    if isinstance(v, (Double, float)):
        return TAG_DOUBLE
    if isinstance(v, str):
        return TAG_STRING
    if isinstance(v, dict):
        return TAG_COMPOUND
    if isinstance(v, list):
        return TAG_LIST
    raise TypeError(type(v))


def _payload(v, out):
    t = _tag_type(v)
    if t == TAG_BYTE:
        out += struct.pack(">b", int(v))
    elif t == TAG_SHORT:
        out += struct.pack(">h", int(v))
    elif t == TAG_INT:
        out += struct.pack(">i", int(v))
    elif t == TAG_LONG:
        out += struct.pack(">q", int(v))
    elif t == TAG_FLOAT:
        out += struct.pack(">f", float(v))
    elif t == TAG_DOUBLE:
        out += struct.pack(">d", float(v))
    elif t == TAG_STRING:
        b = v.encode("utf-8")
        out += struct.pack(">H", len(b)) + b
    elif t == TAG_COMPOUND:
        for k, val in v.items():
            out += struct.pack(">b", _tag_type(val))
            kb = k.encode("utf-8")
            out += struct.pack(">H", len(kb)) + kb
            _payload(val, out)
        out += struct.pack(">b", TAG_END)
    elif t == TAG_LIST:
        if isinstance(v, IntList):
            et = TAG_INT
        elif isinstance(v, DoubleList):
            et = TAG_DOUBLE
        elif isinstance(v, FloatList):
            et = TAG_FLOAT
        elif len(v) == 0:
            et = TAG_END
        else:
            et = _tag_type(v[0])
        out += struct.pack(">bi", et, len(v))
        for item in v:
            if et == TAG_FLOAT:
                out += struct.pack(">f", float(item))
            elif et == TAG_DOUBLE:
                out += struct.pack(">d", float(item))
            elif et == TAG_INT:
                out += struct.pack(">i", int(item))
            else:
                _payload(item, out)


def write(path, root):
    out = bytearray()
    out += struct.pack(">b", TAG_COMPOUND)
    out += struct.pack(">H", 0)
    _payload(root, out)
    # mtime=0 keeps the files byte-identical between runs.
    with open(path, "wb") as raw, gzip.GzipFile(fileobj=raw, mode="wb", mtime=0) as f:
        f.write(bytes(out))
