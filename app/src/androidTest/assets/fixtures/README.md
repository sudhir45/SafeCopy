# Test images

Every image here is made up. `tools/fixtures/generate_fixtures.py` draws the cards with invented names, dates and addresses. The Aadhaar-style numbers are random digits with a valid Verhoeff check digit, the PANs only follow the format, and the QR codes hold random text. No real ID card or real person's details were used.

To regenerate them from the project root:

```bash
python3 -m venv tools/fixtures/.venv
tools/fixtures/.venv/bin/pip install pillow qrcode
tools/fixtures/.venv/bin/python tools/fixtures/generate_fixtures.py
```

`ground_truth.json` lists, for each image, the document type, how far it was turned clockwise (`rotatedCw`), and the box around each sensitive field as `[left, top, right, bottom]` in that image's pixels. Each card comes in 11 versions: plain, turned 90/180/270°, tilted ±8°, heavily compressed, dark, with glare, 800 px wide and 4000 px wide.

Don't add real ID photos here.
