#!/usr/bin/env python3
"""Assert the crosshair is composited at screen center in one screenshot and
absent in the other. Stdlib only (no Pillow needed): decodes the PNG manually.
"""
import struct
import sys
import zlib


def load_png_rgb(path):
    with open(path, "rb") as f:
        data = f.read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "not a PNG: " + path
    pos = 8
    width = height = None
    bit_depth = color_type = None
    idat = bytearray()
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        ctype = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        if ctype == b"IHDR":
            width, height, bit_depth, color_type, comp, filt, interlace = struct.unpack(">IIBBBBB", chunk)
            assert bit_depth == 8 and interlace == 0, "unsupported PNG format"
        elif ctype == b"IDAT":
            idat += chunk
        elif ctype == b"IEND":
            break
        pos += 12 + length
    raw = zlib.decompress(bytes(idat))
    channels = {0: 1, 2: 3, 4: 2, 6: 4}[color_type]
    stride = width * channels
    out = bytearray(height * stride)
    prev = bytearray(stride)
    p = 0
    for y in range(height):
        ftype = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if ftype == 0:
            pass
        elif ftype == 1:
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 0xFF
        elif ftype == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ftype == 3:
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif ftype == 4:
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b = prev[i]
                c = prev[i - channels] if i >= channels else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        else:
            raise ValueError("bad filter " + str(ftype))
        out[y * stride:(y + 1) * stride] = line
        prev = line
    return width, height, channels, out


def magenta_in_center(path, box=400):
    width, height, ch, pix = load_png_rgb(path)
    cx, cy = width // 2, height // 2
    x0, x1 = max(0, cx - box // 2), min(width, cx + box // 2)
    y0, y1 = max(0, cy - box // 2), min(height, cy + box // 2)
    stride = width * ch
    count = 0
    for y in range(y0, y1):
        row = y * stride
        for x in range(x0, x1):
            i = row + x * ch
            r, g, b = pix[i], pix[i + 1], pix[i + 2]
            if r > 180 and b > 180 and g < 120:
                count += 1
    # center pixel for the log
    i = cy * stride + cx * ch
    center = (pix[i], pix[i + 1], pix[i + 2])
    return count, (width, height), center


def main():
    visible_png = "e2e-out/01_settings_visible.png"
    hidden_png = "e2e-out/02_home_hidden.png"

    c1, size, center1 = magenta_in_center(visible_png)
    c2, _, center2 = magenta_in_center(hidden_png)
    print("screen:", size)
    print("visible frame: magenta px in center box =", c1, "center pixel =", center1)
    print("hidden frame:  magenta px in center box =", c2, "center pixel =", center2)

    ok_visible = c1 >= 100
    ok_hidden = c2 <= 10
    print("E2E RESULT:", "PASS" if (ok_visible and ok_hidden) else "FAIL")
    if not ok_visible:
        print("-> crosshair NOT found while targeted app was foreground")
    if not ok_hidden:
        print("-> crosshair was still visible over a non-targeted app")
    sys.exit(0 if (ok_visible and ok_hidden) else 1)


if __name__ == "__main__":
    main()
