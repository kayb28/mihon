"""Verificador de firma de APKs del fork (sin dependencias, solo stdlib).

Extrae el certificado del bloque de firma v2 (APK Signing Block) de cada APK
y compara su huella SHA-256 con el certificado estable del fork (CN=MihonFix,
el que la CI aplica desde `ef0ece3` y que permite instalar encima sin
desinstalar). Uso:

    python3 scripts/verify_apk_signature.py <apk1> [apk2 ...]

Sale con codigo 0 si TODOS los APKs llevan el certificado estable.
"""
import hashlib
import struct
import sys

# Certificado estable del fork: CN=MihonFix (válido 06/11/2026 → 2056).
# Ver historial en docs/REGISTRO.md (06:41 del 06/11) y AGENTS.md.
STABLE_CERT_SHA256 = "DE7889F34D731EF961794F0837B78B86D025387F20E036F357C94742D1296AD0"

BLOCK_MAGIC = b"APK Sig Block 42"
V2_BLOCK_ID = 0x7109871A
V3_BLOCK_ID = 0xF05368C0


def u32(buf, off):
    return struct.unpack_from("<I", buf, off)[0]


def u64(buf, off):
    return struct.unpack_from("<Q", buf, off)[0]


def len_prefixed(buf, off):
    """Devuelve (payload, nuevo_offset) de un campo con longitud uint32 LE."""
    n = u32(buf, off)
    return buf[off + 4: off + 4 + n], off + 4 + n


def cert_sha256(apk_path):
    data = open(apk_path, "rb").read()
    eocd = data.rfind(b"PK\x05\x06")
    if eocd == -1:
        raise ValueError(f"{apk_path}: no es un ZIP/APK (sin EOCD)")
    cd_off = u32(data, eocd + 16)
    if data[cd_off - 16: cd_off] != BLOCK_MAGIC:
        raise ValueError(f"{apk_path}: sin APK Signing Block (¿APK sin firmar?)")
    size = u64(data, cd_off - 24)
    pos = cd_off - size - 8 + 8
    while pos < cd_off - 24:
        pair_len = u64(data, pos)
        pair_id = u32(data, pos + 8)
        if pair_id in (V2_BLOCK_ID, V3_BLOCK_ID):
            value = data[pos + 12: pos + 8 + pair_len]
            signers_seq, _ = len_prefixed(value, 0)
            signer, _ = len_prefixed(signers_seq, 0)
            signed_data, _ = len_prefixed(signer, 0)
            _, off = len_prefixed(signed_data, 0)  # digests
            certs_seq, _ = len_prefixed(signed_data, off)  # certificados
            cert, _ = len_prefixed(certs_seq, 0)  # primer X.509 DER
            return hashlib.sha256(cert).hexdigest().upper()
        pos += 8 + pair_len
    raise ValueError(f"{apk_path}: bloque de firma v2/v3 no encontrado")


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    ok = True
    for path in argv[1:]:
        try:
            fp = cert_sha256(path)
        except ValueError as e:
            print(f"[ERROR] {e}")
            ok = False
            continue
        state = "ESTABLE (instala encima)" if fp == STABLE_CERT_SHA256 else "DISTINTO del certificado estable"
        if fp != STABLE_CERT_SHA256:
            ok = False
        print(f"{path}\n  cert SHA-256: {fp}\n  estado: {state}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
