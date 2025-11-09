#!/usr/bin/env python3
"""
Generate Ed25519 key pair for Phalanx sender pack signing.

This script generates:
1. Private key (keep secret!)
2. Public key (embed in app)
3. Test signature for verification

Requirements: pip install cryptography
"""

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives import serialization
import base64
import json

def generate_keypair():
    """Generate Ed25519 key pair"""
    print("Generating Ed25519 key pair...")

    # Generate private key
    private_key = Ed25519PrivateKey.generate()

    # Get public key
    public_key = private_key.public_key()

    # Serialize private key (PEM format)
    private_pem = private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption()
    )

    # Serialize public key (X.509 SubjectPublicKeyInfo format - same as Java expects)
    public_der = public_key.public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo
    )

    # Base64 encode public key for Java/Kotlin
    public_base64 = base64.b64encode(public_der).decode('ascii')

    print("\n" + "="*80)
    print("ED25519 KEY PAIR GENERATED")
    print("="*80)

    print("\n[1] PUBLIC KEY (for SignatureVerifier.kt)")
    print("-" * 80)
    print(f'private const val PUBLIC_KEY_BASE64 = "{public_base64}"')
    print("-" * 80)

    print("\n[2] PRIVATE KEY (KEEP SECRET!)")
    print("-" * 80)
    print("WARNING: NEVER commit this to version control!")
    print("WARNING: Store in secure location (password manager, hardware token, etc.)")
    print("-" * 80)
    print(private_pem.decode('ascii'))

    # Test signature
    test_message = b"Test message for Phalanx sender pack"
    signature = private_key.sign(test_message)
    signature_hex = signature.hex()

    print("\n[3] TEST SIGNATURE")
    print("-" * 80)
    print(f"Message: {test_message.decode('ascii')}")
    print(f"Signature (hex): {signature_hex}")
    print("-" * 80)

    # Verify signature works
    try:
        public_key.verify(signature, test_message)
        print("[OK] Signature verification: SUCCESS")
    except Exception as e:
        print(f"[FAIL] Signature verification: FAILED - {e}")

    # Save keys to files
    print("\n[4] SAVING TO FILES")
    print("-" * 80)

    with open("ed25519_private_key.pem", "wb") as f:
        f.write(private_pem)
    print("[OK] Private key saved to: ed25519_private_key.pem")

    with open("ed25519_public_key.txt", "w") as f:
        f.write(public_base64)
    print("[OK] Public key saved to: ed25519_public_key.txt")

    # Save signing instructions
    with open("SIGNING_INSTRUCTIONS.md", "w", encoding="utf-8") as f:
        f.write(f"""# Ed25519 Signing Instructions for Phalanx

## Key Pair Information

**Generated:** {__import__('datetime').datetime.now().isoformat()}

### Public Key (Base64)
```
{public_base64}
```

### Files
- `ed25519_private_key.pem` - Private key (⚠️ KEEP SECRET!)
- `ed25519_public_key.txt` - Public key (safe to embed in app)

## How to Sign a Sender Pack

### Using Python (cryptography library)

```python
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
import json

# Load private key
with open("ed25519_private_key.pem", "rb") as f:
    private_key = serialization.load_pem_private_key(f.read(), password=None)

# Load sender pack JSON
with open("IN.json", "r", encoding="utf-8") as f:
    pack_json = f.read()

# Sign the JSON (exactly as stored in file)
signature = private_key.sign(pack_json.encode('utf-8'))
signature_hex = signature.hex()

print(f"Signature: {{signature_hex}}")

# Update the pack with signature
pack = json.loads(pack_json)
pack["signature"] = signature_hex

# Save updated pack
with open("IN.json", "w", encoding="utf-8") as f:
    json.dump(pack, f, indent=2, ensure_ascii=False)
```

### Using OpenSSL (command line)

```bash
# Sign file (produces binary signature)
openssl pkeyutl -sign -inkey ed25519_private_key.pem -rawin -in IN.json -out signature.bin

# Convert to hex
xxd -p signature.bin | tr -d '\\n' > signature.hex

# Show signature
cat signature.hex
```

## Security Best Practices

1. **NEVER commit private key to git**
2. Store private key in password manager or hardware security module
3. Rotate keys periodically (every 6-12 months)
4. Keep backup of private key in secure offline location
5. Never email or share private key
6. Public key can be safely embedded in app code

## Updating the App

1. Copy public key to `SignatureVerifier.kt`:
   ```kotlin
   private const val PUBLIC_KEY_BASE64 = "{public_base64}"
   ```

2. Sign all sender packs (IN.json, US.json, etc.)

3. Deploy updated packs via app update or remote config

## Key Rotation Procedure

When rotating keys:
1. Generate new key pair
2. Sign all packs with new key
3. Update app with new public key
4. Deploy app update
5. After 95% users updated, retire old key
6. Securely destroy old private key

---
Generated by generate_ed25519_keypair.py
""")
    print("[OK] Instructions saved to: SIGNING_INSTRUCTIONS.md")

    print("\n" + "="*80)
    print("NEXT STEPS:")
    print("="*80)
    print("1. Update SignatureVerifier.kt with the public key above")
    print("2. Use sign_sender_pack.py to sign IN.json")
    print("3. Store ed25519_private_key.pem in a secure location")
    print("4. Add ed25519_private_key.pem to .gitignore")
    print("="*80)

    return private_key, public_key, public_base64

if __name__ == "__main__":
    try:
        generate_keypair()
    except ImportError:
        print("❌ Error: cryptography library not installed")
        print("Run: pip install cryptography")
        exit(1)
