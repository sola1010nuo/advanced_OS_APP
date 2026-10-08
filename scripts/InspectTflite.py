"""Inspect TFLite FlatBuffer tensors and arithmetic constants without ML dependencies."""
import json
import struct
from pathlib import Path

def inspect(path):
    data = path.read_bytes()
    def u32(offset): return struct.unpack_from("<I", data, offset)[0]
    def i32(offset): return struct.unpack_from("<i", data, offset)[0]
    def field(table, index):
        vtable = table - i32(table)
        slot = vtable + 4 + index * 2
        if slot >= vtable + struct.unpack_from("<H", data, vtable)[0]:
            return 0
        relative = struct.unpack_from("<H", data, slot)[0]
        return table + relative if relative else 0
    def indirect(offset): return offset + u32(offset)
    def vector(table, index):
        offset = field(table, index)
        if not offset: return (0, 0)
        start = indirect(offset)
        return start + 4, u32(start)
    def ints(table, index):
        start, count = vector(table, index)
        return list(struct.unpack_from("<" + "i" * count, data, start)) if count else []
    def tables(table, index):
        start, count = vector(table, index)
        return [indirect(start + i * 4) for i in range(count)]
    def string(table, index):
        offset = field(table, index)
        if not offset: return ""
        start = indirect(offset)
        return data[start + 4:start + 4 + u32(start)].decode("utf-8")
    def scalar(table, index, fmt, default=0):
        offset = field(table, index)
        return struct.unpack_from(fmt, data, offset)[0] if offset else default

    model = u32(0)
    buffers = tables(model, 4)
    opcodes = tables(model, 1)
    subgraph = tables(model, 2)[0]
    tensors = tables(subgraph, 0)
    types = {0: "FLOAT32", 1: "FLOAT16", 2: "INT32", 3: "UINT8", 9: "INT8"}
    def tensor(index):
        table = tensors[index]
        info = dict(index=index, name=string(table, 3), shape=ints(table, 0),
                    dtype=types.get(scalar(table, 1, "<B"), str(scalar(table, 1, "<B"))))
        buffer = buffers[scalar(table, 2, "<I")]
        start, length = vector(buffer, 0)
        if info["dtype"] == "FLOAT32" and 0 < length <= 48 and length % 4 == 0:
            info["constant"] = list(struct.unpack_from("<" + "f" * (length // 4), data, start))
        quant = field(table, 4)
        if quant:
            q = indirect(quant)
            scale, n = vector(q, 2)
            zero, m = vector(q, 3)
            info["quantization"] = dict(
                scale=list(struct.unpack_from("<" + "f" * n, data, scale)) if n else [],
                zero_point=list(struct.unpack_from("<" + "q" * m, data, zero)) if m else [])
        return info
    operations = []
    names = {0: "ADD", 3: "CONV_2D", 4: "DEPTHWISE_CONV_2D", 18: "MUL",
             22: "RESHAPE", 25: "SOFTMAX", 40: "MEAN", 41: "SUB", 42: "DIV"}
    for operator in tables(subgraph, 3):
        opcode = opcodes[scalar(operator, 0, "<I")]
        code = max(scalar(opcode, 0, "<b"), scalar(opcode, 3, "<i"))
        inputs = ints(operator, 1)
        operations.append(dict(op=names.get(code, str(code)),
                               inputs=[tensor(i) for i in inputs if i >= 0],
                               outputs=ints(operator, 2)))
    return dict(file=path.name, inputs=[tensor(i) for i in ints(subgraph, 1)],
                outputs=[tensor(i) for i in ints(subgraph, 2)],
                first_operations=operations[:2], last_operations=operations[-1:])

if __name__ == "__main__":
    assets = Path("app/src/main/assets")
    print(json.dumps([inspect(assets / name) for name in
        ["cat_dog_monkey_model.tflite", "cat_model.tflite", "dog_model.tflite"]], indent=2))