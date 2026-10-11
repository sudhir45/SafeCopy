# Synthetic test fixtures

Every image here is **synthetic**. The names, dates, addresses, Aadhaar-style numbers, VIDs and PANs are made up by `tools/fixtures/generate_fixtures.py`. Aadhaar-style numbers are random digits made Verhoeff-valid. QR codes contain random text. No real identity document or real person's data was used.

Regenerate from the project root:

```bash
python3 -m venv tools/fixtures/.venv
tools/fixtures/.venv/bin/pip install pillow qrcode
tools/fixtures/.venv/bin/python tools/fixtures/generate_fixtures.py
```

`ground_truth.json` lists each image's document type, how far it was rotated clockwise (`rotatedCw`), and the boxes of its sensitive fields as `[left, top, right, bottom]` in that image's pixels. Each card has these variants: base, rot90/180/270, tilt ±8°, JPEG quality 20, dark, glare, 800 px and 4000 px.

Never add real ID photos here. Use the git-ignored `private_samples/` folder for personal manual testing.
