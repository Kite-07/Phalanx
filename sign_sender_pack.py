#!/usr/bin/env python3
"""
Sign a Phalanx sender pack with Ed25519 private key.

Usage:
    python sign_sender_pack.py IN.json

Requirements: pip install cryptography
"""

import sys
import json
from cryptography.hazmat.primitives import serialization
from pathlib import Path

def sign_pack(pack_path: str, private_key_path: str = "ed25519_private_key.pem"):
    """Sign a sender pack JSON file"""

    # Check if private key exists
    if not Path(private_key_path).exists():
        print(f"[ERROR] Private key not found: {private_key_path}")
        print("Run generate_ed25519_keypair.py first to create key pair")
        return False

    # Check if pack exists
    if not Path(pack_path).exists():
        print(f"[ERROR] Sender pack not found: {pack_path}")
        return False

    print(f"Signing sender pack: {pack_path}")
    print(f"Using private key: {private_key_path}")

    # Load private key
    print("\n[1] Loading private key...")
    with open(private_key_path, "rb") as f:
        private_key = serialization.load_pem_private_key(f.read(), password=None)
    print("[OK] Private key loaded")

    # Load pack JSON
    print("\n[2] Loading sender pack...")
    with open(pack_path, "r", encoding="utf-8") as f:
        pack_json = f.read()

    pack = json.loads(pack_json)
    print(f"[OK] Pack loaded: region={pack.get('region')}, version={pack.get('version')}, entries={len(pack.get('entries', []))}")

    # Create canonical JSON for signing (without signature field)
    # This is the message that will be signed
    pack_for_signing = {k: v for k, v in pack.items() if k != 'signature'}
    canonical_json = json.dumps(pack_for_signing, sort_keys=True, ensure_ascii=False, separators=(',', ':'))

    print(f"\n[3] Canonical JSON ({len(canonical_json)} bytes):")
    print(f"    {canonical_json[:100]}..." if len(canonical_json) > 100 else f"    {canonical_json}")

    # Sign the canonical JSON
    print("\n[4] Signing...")
    signature = private_key.sign(canonical_json.encode('utf-8'))
    signature_hex = signature.hex()
    print(f"[OK] Signature generated: {signature_hex[:32]}...{signature_hex[-32:]}")

    # Verify signature immediately
    print("\n[5] Verifying signature...")
    try:
        public_key = private_key.public_key()
        public_key.verify(signature, canonical_json.encode('utf-8'))
        print("[OK] Signature verification: SUCCESS")
    except Exception as e:
        print(f"[FAIL] Signature verification: FAILED - {e}")
        return False

    # Update pack with signature
    print("\n[6] Updating pack with signature...")
    pack['signature'] = signature_hex

    # Save updated pack (pretty-printed for readability)
    with open(pack_path, "w", encoding="utf-8") as f:
        json.dump(pack, f, indent=2, ensure_ascii=False)

    print(f"[OK] Pack updated: {pack_path}")

    print("\n" + "="*80)
    print("SIGNING COMPLETE")
    print("="*80)
    print(f"Region: {pack.get('region')}")
    print(f"Version: {pack.get('version')}")
    print(f"Entries: {len(pack.get('entries', []))}")
    print(f"Signature: {signature_hex}")
    print("="*80)

    return True

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python sign_sender_pack.py <pack_file.json> [private_key.pem]")
        print("\nExample:")
        print("  python sign_sender_pack.py app/src/main/assets/sender_packs/IN.json")
        sys.exit(1)

    pack_path = sys.argv[1]
    private_key_path = sys.argv[2] if len(sys.argv) > 2 else "ed25519_private_key.pem"

    try:
        success = sign_pack(pack_path, private_key_path)
        sys.exit(0 if success else 1)
    except ImportError:
        print("[ERROR] cryptography library not installed")
        print("Run: pip install cryptography")
        sys.exit(1)
    except Exception as e:
        print(f"[ERROR] {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
