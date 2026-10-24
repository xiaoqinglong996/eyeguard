#!/usr/bin/env python3
# 纯 Python 生成多分辨率 PNG 图标（无第三方库），替换矢量图标以消除启动崩溃隐患。
import os, zlib, struct, math

ROOT = r"C:/users/administrator/workbuddy/2026-10-23-16-51-22/eyeguard/app/src/main/res"
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
SS = 4  # 超采样倍数，用于抗锯齿

def write_png(path, w, h, rgba):
    def chunk(typ, data):
        return struct.pack(">I", len(data)) + typ + data + struct.pack(">I", zlib.crc32(typ + data) & 0xffffffff)
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        raw += rgba[y*w*4:(y+1)*w*4]
    idat = zlib.compress(bytes(raw), 9)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)))
        f.write(chunk(b"IDAT", idat))
        f.write(chunk(b"IEND", b""))

def render(logical, mode):
    """mode='launcher' -> 蓝色背景+白眼；mode='notify' -> 白色眼形(透明底)。返回 NxN RGBA。"""
    N = logical
    hi = N * SS
    cx = hi / 2.0
    buf = bytearray(hi * hi * 4)
    for y in range(hi):
        for x in range(hi):
            # 逻辑坐标 [-0.5,0.5]
            fx = (x - cx) / N
            fy = (y - cy) / N if False else (y - cx) / N  # cy==cx
            fy = (y - cx) / N
            d = math.hypot(fx, fy)
            if mode == "launcher":
                col = (21, 101, 192, 255)  # 蓝底
                if (fx/0.40)**2 + (fy/0.26)**2 <= 1.0:
                    col = (255, 255, 255, 255)  # 眼白
                if d <= 0.18:
                    col = (13, 42, 74, 255)      # 虹膜
                if d <= 0.09:
                    col = (0, 0, 0, 255)         # 瞳孔
            else:  # notify: 白色眼形，透明底
                col = (0, 0, 0, 0)
                in_eye = (fx/0.42)**2 + (fy/0.27)**2 <= 1.0
                in_pupil = d <= 0.11
                if in_eye and not in_pupil:
                    col = (255, 255, 255, 255)
            i = (y*hi + x) * 4
            buf[i:i+4] = bytes(col)
    # 降采样到 NxN
    out = bytearray(N*N*4)
    for oy in range(N):
        for ox in range(N):
            r=g=b=a=0
            for sy in range(SS):
                for sx in range(SS):
                    p = ((oy*SS+sy)*hi + (ox*SS+sx)) * 4
                    r += buf[p]; g += buf[p+1]; b += buf[p+2]; a += buf[p+3]
            n = SS*SS
            q = (oy*N + ox) * 4
            out[q] = r//n; out[q+1] = g//n; out[q+2] = b//n; out[q+3] = a//n
    return out

def main():
    for dens, N in SIZES.items():
        for name, mode in (("ic_launcher", "launcher"), ("ic_notify", "notify")):
            d = os.path.join(ROOT, f"mipmap-{dens}")
            os.makedirs(d, exist_ok=True)
            p = os.path.join(d, f"{name}.png")
            write_png(p, N, N, render(N, mode))
            print("wrote", p)
    # 删除旧的矢量图标，避免与 @drawable/ic_eye 命名冲突（改用 mipmap）
    old = os.path.join(ROOT, "drawable", "ic_eye.xml")
    if os.path.exists(old):
        os.remove(old)
        print("removed", old)

if __name__ == "__main__":
    main()
