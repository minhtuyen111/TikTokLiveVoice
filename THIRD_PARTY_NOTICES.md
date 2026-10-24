# Third-party notices: offline Vietnamese speech

This app's CI build downloads and packages the following components:

- **sherpa-onnx Android runtime v1.13.8**: Apache License 2.0. Source: https://github.com/k2-fsa/sherpa-onnx
- **Piper Vietnamese voice `vi_VN-vais1000-medium`**: source model: https://huggingface.co/rhasspy/piper-voices/tree/main/vi/vi_VN/vais1000/medium . The model card identifies the VAIS-1000 corpus as CC BY 4.0; retain attribution to the dataset and model authors when redistributing. Model card: https://huggingface.co/rhasspy/piper-voices/blob/main/vi/vi_VN/vais1000/medium/MODEL_CARD

The voice is a medium-quality, single-speaker neural model at 22.05 kHz. It is an offline fallback, not a promise of identical sound to Azure Neural TTS on every device.

Note: the Piper/sherpa-onnx phonemizer uses **espeak-ng** data (GPL-3.0-or-later). Review the licence obligations before distributing the APK publicly.
