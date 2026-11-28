# INFORME — Cadena de custodia de APKs: ¿se perdió algo entre build y build?

_PROMPT 11 · forensics, sin cambios de código · 2026-11-28 (UTC)_
_Repo: kayb28/mihonfix · workflow «Build APK» (`.github/workflows/build_apk.yml`) · 210 runs exitosos_

## Método

- Inventario vía API de GitHub de **todos los runs exitosos** del workflow «Build APK»
  (393 runs totales, 210 exitosos) y sus **518 artefactos vivos**: nombre, bytes,
  **digest oficial del API** y `expires_at` de cada uno (§1).
- Verificación por artefacto (§2): descarga del zip → **sha256 del zip contra el digest
  del API** (sello ✅) → extracción del APK arm64 (línea instalable) → sha256 + tamaño
  reales → firma v2/v3 contra el certificado estable (verificador propio
  `scripts/verify_apk_signature.py`) → **huella de contenido**: clases y métodos
  definidos de todos los `classes*.dex`, como lista estable
  `L…;->nombre(firma)` ordenada, con hash propio.
- El digest del API es el sha256 del **zip del artefacto**; el sha256 del APK de dentro
  se registra aparte. «Sello ✅» = el zip descargado es bit a bit el que CI subió.
- Cobertura (regla de prioridad del encargo): **57 artefactos verificados** = las 9
  primeras builds (29–30/10, las que caducan 28–29/11), la **línea `main` completa**
  (34 builds release, 02/11→27/11) y el **episodio caliente del 04/11**
  (`feature/webgpu-clean`, 14 builds 03–05/11). El resto (461) queda «solo histórico»:
  digest y tamaño anotados en §1, bytes no descargados en este encargo.
- Limitación honesta: entre el 05/11 y el 10/11 la línea de distribución era
  `feature/webgpu-clean` (builds no descargadas aquí); la continuidad DEX de esa semana
  se observa solo por sus extremos (05/11 → 11/11), no build a build.

## 1. Inventario vivo (C0) — 518 artefactos, orden cronológico

Al momento del inventario (28/11, ~00:20 UTC) **ningún artefacto había expirado**:
los más viejos (runs 3 y 5, del 29/10) expiran el propio 28/11 y siguen vivos. **Cero
bytes perdidos por expiración a esta fecha.**

| run | fecha (UTC) | rama | sha | artefacto | bytes | digest (sha256 del zip) | expira | estado |
|---|---|---|---|---|---|---|---|---|
| 3 | 2026-10-29 02:14 | `fix-0.21.3` | `60e1288c17` | mihon-release-apks-60e1288c170b0f15ed817f3f129d7f9a3bf17884 | 105612436 | `sha256:5e7fc2b154e6a67f9c52a66fc9cac27eb88db02986b34fdd16c1b77510ee0d66` | 2026-11-28 | ✅ bytes verificados |
| 3 | 2026-10-29 02:14 | `fix-0.21.3` | `60e1288c17` | mihon-unminified-apks-60e1288c170b0f15ed817f3f129d7f9a3bf17884 | 105612428 | `sha256:51dd8904adbd6043681841fc1a59fc5b111fd3b6638df973a783c2b9a18ff7d9` | 2026-11-28 | solo histórico |
| 5 | 2026-10-29 21:57 | `feature/source-management` | `66274d7df9` | mihon-release-apks-66274d7df9854af3be74c22b63ee61522db3b355 | 105704540 | `sha256:3357c4f44200b6cdae342f2835612c06a2f7d7f857cbf63ce8088f15cbc179d9` | 2026-11-28 | ✅ bytes verificados |
| 5 | 2026-10-29 21:57 | `feature/source-management` | `66274d7df9` | mihon-unminified-apks-66274d7df9854af3be74c22b63ee61522db3b355 | 105704519 | `sha256:6963ab8f966f7c26b87605e2ddb6784b1ef2b8760c435f6f68a72ad5f2bfd512` | 2026-11-28 | solo histórico |
| 6 | 2026-10-29 22:20 | `feature/source-management` | `4151d80ef0` | mihon-release-apks-4151d80ef0a5d0b1b769001cfcc1e0ea58c69705 | 105705083 | `sha256:c51e484063939c676d9a52e9d20191aa7c83027af6dbe687cb3519d4613d2de3` | 2026-11-28 | ✅ bytes verificados |
| 6 | 2026-10-29 22:20 | `feature/source-management` | `4151d80ef0` | mihon-unminified-apks-4151d80ef0a5d0b1b769001cfcc1e0ea58c69705 | 105705084 | `sha256:f392756a1d0733250279c3804b051e9c7af274ab9bbe6002d1b33213e13e4fbd` | 2026-11-28 | solo histórico |
| 7 | 2026-10-30 02:36 | `feature/source-management` | `61b24dc20d` | mihon-release-apks-61b24dc20dd1244a604fcc52b4105837b3be7219 | 105723672 | `sha256:dc7ef313c4c2efa19fd474d99d01cef78744613cceb283d4b3f2aed65008ec35` | 2026-11-29 | ✅ bytes verificados |
| 7 | 2026-10-30 02:36 | `feature/source-management` | `61b24dc20d` | mihon-unminified-apks-61b24dc20dd1244a604fcc52b4105837b3be7219 | 105723658 | `sha256:279a9d69f8d47d90b8b3c89425662d8504053dd5fafe81e8fb2d22fa8423db9f` | 2026-11-29 | solo histórico |
| 9 | 2026-10-30 02:54 | `fix/repair-links-main` | `b1d69aeb62` | mihon-release-apks-b1d69aeb620ed378f8bddd8d9170120f3afe27d3 | 139164355 | `sha256:f56e64fc5cb1d038324ee438500a029b9e061c20ac3334d5a97481b9d4641ff4` | 2026-11-29 | ✅ bytes verificados |
| 9 | 2026-10-30 02:54 | `fix/repair-links-main` | `b1d69aeb62` | mihon-unminified-apks-b1d69aeb620ed378f8bddd8d9170120f3afe27d3 | 217831016 | `sha256:a44d6b109aa7f8194197b67e93a580e5d2b902f870d2e217338ed93d31faf640` | 2026-11-29 | solo histórico |
| 10 | 2026-10-30 03:12 | `feature/source-management` | `b481448499` | mihon-release-apks-b48144849954868edb13b48ed4bef49cf8b76cc7 | 105725333 | `sha256:120d698d5ecea464049f9f450942a128f4fc7d15dd88c5ec5b671b7a364e7e86` | 2026-11-29 | ✅ bytes verificados |
| 10 | 2026-10-30 03:12 | `feature/source-management` | `b481448499` | mihon-unminified-apks-b48144849954868edb13b48ed4bef49cf8b76cc7 | 105725315 | `sha256:19df2c9537b282add70462777615253e1322b34bad6b4e995e12fde7d9016f59` | 2026-11-29 | solo histórico |
| 12 | 2026-10-30 16:48 | `integration` | `5567a80262` | mihon-release-apks-5567a802626841e8b5ff72169e216bb6be42ebe9 | 139250375 | `sha256:161cd8c6e8b17c7e5e914d2929fe97613b3b9ca3782fa440529c71caff4d5809` | 2026-11-29 | ✅ bytes verificados |
| 12 | 2026-10-30 16:48 | `integration` | `5567a80262` | mihon-unminified-apks-5567a802626841e8b5ff72169e216bb6be42ebe9 | 217987552 | `sha256:ee8c0dd66061b88c876e486d81dcfd66a5ed42c3b25e9f55386222b3a6b1fa16` | 2026-11-29 | solo histórico |
| 13 | 2026-10-30 16:58 | `integration` | `3470c9c5e4` | mihon-release-apks-3470c9c5e441f0c1f16c91794615471a00e01ab3 | 139249969 | `sha256:b98b34773edd1fb2fbda99b83aff688a2e959170107cda421d3fd64d85cb6675` | 2026-11-29 | ✅ bytes verificados |
| 13 | 2026-10-30 16:58 | `integration` | `3470c9c5e4` | mihon-unminified-apks-3470c9c5e441f0c1f16c91794615471a00e01ab3 | 217987587 | `sha256:fc2a4ced24d108e3ea34cb3fc89004c9d45897d4b4e9ab3e8b5ddff26e75a3db` | 2026-11-29 | solo histórico |
| 14 | 2026-10-30 17:24 | `integration` | `7fb14b5911` | mihon-release-apks-7fb14b5911610256436b56071efd2c948f514960 | 139276597 | `sha256:abdef2039754d7ab4bb488189e2ee134f9d23bdb7738bcebd14b47e088334619` | 2026-11-29 | ✅ bytes verificados |
| 14 | 2026-10-30 17:24 | `integration` | `7fb14b5911` | mihon-unminified-apks-7fb14b5911610256436b56071efd2c948f514960 | 217943062 | `sha256:4f14b8149288371fe07bc56f1e21b91ea02749a04d1813e2615b3e29f58691f2` | 2026-11-29 | solo histórico |
| 23 | 2026-10-31 18:22 | `fix/build-green` | `f7ef6e336e` | mihon-release-apks-f7ef6e336e96bfc19b231d921c073893711a836a | 105611976 | `sha256:add17d1ed1290541dab1494fbea18bd8445e80a334dc35d40172ef3e0a179924` | 2026-11-30 | solo histórico |
| 23 | 2026-10-31 18:22 | `fix/build-green` | `f7ef6e336e` | mihon-unminified-apks-f7ef6e336e96bfc19b231d921c073893711a836a | 105611988 | `sha256:8c9b263c8c9eb7943b1f1f99861a1298f5aea495ada726fa1a8c6eb92b6ed4f2` | 2026-11-30 | solo histórico |
| 26 | 2026-10-31 22:26 | `feature/webgpu-androidx` | `7cd88da57a` | mihon-release-apks-7cd88da57ad48a959be6abc87969fd8e19cc8001 | 139276715 | `sha256:3ba3bd28492dd1bbb6001763c5bcff21b7019d686502ebc62a422488f7fbb4f2` | 2026-11-30 | solo histórico |
| 26 | 2026-10-31 22:26 | `feature/webgpu-androidx` | `7cd88da57a` | mihon-unminified-apks-7cd88da57ad48a959be6abc87969fd8e19cc8001 | 217944870 | `sha256:e3735b55e1472abd28bd9601c86f7de1a3409d70753676cfc4d98cb5b6d6fea5` | 2026-11-30 | solo histórico |
| 27 | 2026-10-31 22:38 | `feature/webgpu-androidx` | `f1bdc61239` | mihon-release-apks-f1bdc6123973e032ea8e4aed850cf999836808aa | 139276130 | `sha256:db08827b0774acc9abd9d9f4794a5d775ee8cb9668facf99755db3fbfb4443ad` | 2026-11-30 | solo histórico |
| 27 | 2026-10-31 22:38 | `feature/webgpu-androidx` | `f1bdc61239` | mihon-unminified-apks-f1bdc6123973e032ea8e4aed850cf999836808aa | 217945050 | `sha256:acea7cb8f24ae7e2f7387c15b2e70d63885dff406571a8140542ac5a414b6f36` | 2026-11-30 | solo histórico |
| 28 | 2026-10-31 23:13 | `feature/webgpu-androidx` | `8bbf65e348` | mihon-release-apks-8bbf65e3486f42ca6ae25f2db169b00f3262ea15 | 139280031 | `sha256:f80b823ad0b7f33e0ea02f275c84963122e802571e27fe1a3f24e2649a715924` | 2026-11-30 | solo histórico |
| 28 | 2026-10-31 23:13 | `feature/webgpu-androidx` | `8bbf65e348` | mihon-unminified-apks-8bbf65e3486f42ca6ae25f2db169b00f3262ea15 | 217946163 | `sha256:96ab417f9fff0d213fc5e4beb205db51f28868ab53d9f5b79c74dce0986ac7e5` | 2026-11-30 | solo histórico |
| 29 | 2026-10-31 23:32 | `feature/webgpu-androidx` | `fff4710e59` | mihon-release-apks-fff4710e59e7c84fe56d01a1c43cc3d941b81be2 | 139293612 | `sha256:ec468e9f5b976aeb9696895f32ea2905a0132d4b89284753cd6d821a3ca40144` | 2026-11-30 | solo histórico |
| 29 | 2026-10-31 23:32 | `feature/webgpu-androidx` | `fff4710e59` | mihon-unminified-apks-fff4710e59e7c84fe56d01a1c43cc3d941b81be2 | 217967087 | `sha256:a164afddb73cc00174cdf7a4b611a36efd9394c77d54b96d3dc9c00a6a1d03ff` | 2026-11-30 | solo histórico |
| 31 | 2026-11-01 00:15 | `feature/webgpu-clean` | `d52ffe84e8` | mihon-release-apks-d52ffe84e8783302bc5623ace853187fcefce68d | 139298883 | `sha256:54bcce3a1bb86273f9d69ae1b6879b47bc45dee41cce771935bb45fdc9b0a0b4` | 2026-12-01 | solo histórico |
| 31 | 2026-11-01 00:15 | `feature/webgpu-clean` | `d52ffe84e8` | mihon-unminified-apks-d52ffe84e8783302bc5623ace853187fcefce68d | 217981378 | `sha256:b7a78cd97a5a2e48e22d48e1e2dfcb784e3d7c15c6d934f3701ef5f0b8033012` | 2026-12-01 | solo histórico |
| 33 | 2026-11-01 00:49 | `feature/webgpu-clean` | `ebb4d07a50` | mihon-release-apks-ebb4d07a503fea78dcd23afe4079cce4b7936bb8 | 139301405 | `sha256:daa787fbbf026e36be7131ec4c0b3fd15dde48cbccebc92ae5b90339b85c0dc4` | 2026-12-01 | solo histórico |
| 33 | 2026-11-01 00:49 | `feature/webgpu-clean` | `ebb4d07a50` | mihon-unminified-apks-ebb4d07a503fea78dcd23afe4079cce4b7936bb8 | 217985425 | `sha256:91ab3c00ec001a76676e999bbe338720e95d9dfac891e182434848d7873c70ba` | 2026-12-01 | solo histórico |
| 35 | 2026-11-01 03:53 | `feature/webgpu-clean` | `4a0d2fbba0` | mihon-release-apks-4a0d2fbba06452772db357d000ae1bb226697f78 | 139268610 | `sha256:9fdcf8966635786b043574b5866c94aea6798016c730a747fa57f03d7651edd9` | 2026-12-01 | solo histórico |
| 35 | 2026-11-01 03:53 | `feature/webgpu-clean` | `4a0d2fbba0` | mihon-unminified-apks-4a0d2fbba06452772db357d000ae1bb226697f78 | 218064380 | `sha256:86152feb1d79ea4ebc6021a887505dba5e94753b14d6a32bcc8eee0a6750316e` | 2026-12-01 | solo histórico |
| 36 | 2026-11-01 04:14 | `feature/webgpu-clean` | `8d28faf6ef` | mihon-release-apks-8d28faf6ef2e0739c6df8ac036e1c089c60c608b | 139295914 | `sha256:13f7a0ec59889f6bb365dda57d6c8a80435d9bf0731907cd6ae6e7b0108b277b` | 2026-12-01 | solo histórico |
| 36 | 2026-11-01 04:14 | `feature/webgpu-clean` | `8d28faf6ef` | mihon-unminified-apks-8d28faf6ef2e0739c6df8ac036e1c089c60c608b | 218070543 | `sha256:c6b61b1eca2d47f4d9c3beae199714068389ee12d9a18737280dfe83d497e85c` | 2026-12-01 | solo histórico |
| 37 | 2026-11-01 04:33 | `feature/webgpu-clean` | `ae27612fea` | mihon-release-apks-ae27612feabd07e8cf4eed2c85af45a0116c8e77 | 139305618 | `sha256:6e0be34003ed0f4982f7deef1c9a43525ea979fa70caf1f4bcd3aa01d491b6af` | 2026-12-01 | solo histórico |
| 37 | 2026-11-01 04:33 | `feature/webgpu-clean` | `ae27612fea` | mihon-unminified-apks-ae27612feabd07e8cf4eed2c85af45a0116c8e77 | 218087030 | `sha256:fc0ee2f1950a1f3341e854b4bfdbec88a183ed3fbc9294e261dbca5e4ad6c80f` | 2026-12-01 | solo histórico |
| 38 | 2026-11-01 04:55 | `feature/webgpu-clean` | `8d424a495e` | mihon-release-apks-8d424a495e38dbe9b02377c8d37152b28c8414de | 139311884 | `sha256:c777e49e9d75f948a8235fc89718d01853f6557f4e588784311fb39541543bfb` | 2026-12-01 | solo histórico |
| 38 | 2026-11-01 04:55 | `feature/webgpu-clean` | `8d424a495e` | mihon-unminified-apks-8d424a495e38dbe9b02377c8d37152b28c8414de | 218094578 | `sha256:4e04e0fca55ed2274989e03783a04458ada79e821d6e0691ac840face1ed1b3e` | 2026-12-01 | solo histórico |
| 39 | 2026-11-01 05:20 | `feature/webgpu-clean` | `a8b5feb629` | mihon-release-apks-a8b5feb629faf9086381ae881400ab5b11376644 | 139313664 | `sha256:b69741fe0bf2dd92e96ed150a938b608ed9f194331c80cb51f884857f42fd011` | 2026-12-01 | solo histórico |
| 39 | 2026-11-01 05:20 | `feature/webgpu-clean` | `a8b5feb629` | mihon-unminified-apks-a8b5feb629faf9086381ae881400ab5b11376644 | 218108121 | `sha256:d98ed4532a87a1db60679471c0fe5fbc7646c0a3eab56a7c1231a4ad6cd249f2` | 2026-12-01 | solo histórico |
| 40 | 2026-11-01 05:38 | `feature/webgpu-clean` | `cac5014ff7` | mihon-release-apks-cac5014ff74d157a436fb3462fee9e20ff70c6a8 | 139348368 | `sha256:113e6ecef05ba472fc23441ffdda1467161043d77bd2a9a3f9fa79bdc4a2bb1b` | 2026-12-01 | solo histórico |
| 40 | 2026-11-01 05:38 | `feature/webgpu-clean` | `cac5014ff7` | mihon-unminified-apks-cac5014ff74d157a436fb3462fee9e20ff70c6a8 | 218139896 | `sha256:9407dfc6b25bc12a5611b245897a503e650a8f7691d0f3399b9afa4b3d7d9b6a` | 2026-12-01 | solo histórico |
| 41 | 2026-11-01 05:57 | `feature/webgpu-clean` | `4fc1233be5` | mihon-release-apks-4fc1233be5387bea77b0170c76d0c9e4a0039e3d | 139349681 | `sha256:06cc37d027f16ac0a9e1bf5d80f909b2693ba637855a456000ea89f4a8c5cede` | 2026-12-01 | solo histórico |
| 41 | 2026-11-01 05:57 | `feature/webgpu-clean` | `4fc1233be5` | mihon-unminified-apks-4fc1233be5387bea77b0170c76d0c9e4a0039e3d | 218149600 | `sha256:1e532032ff9525163a5d11c5ca895ebf3d1e9eb091ee073a289c2f3362cbef47` | 2026-12-01 | solo histórico |
| 42 | 2026-11-01 06:18 | `feature/webgpu-clean` | `7ae2f8f703` | mihon-release-apks-7ae2f8f70335dc388a4346edd0dfa19e55492e6c | 139358243 | `sha256:e52b7aa73e92a9efdb1fcb5677ef003d884eb8558b7340c4d90cf65082b2df0d` | 2026-12-01 | solo histórico |
| 42 | 2026-11-01 06:18 | `feature/webgpu-clean` | `7ae2f8f703` | mihon-unminified-apks-7ae2f8f70335dc388a4346edd0dfa19e55492e6c | 218153411 | `sha256:7c0f30e4bcfc1b76deecc7bb6f9a7a36e916840049a8c0a08d1e6697c4dd4a8d` | 2026-12-01 | solo histórico |
| 43 | 2026-11-01 06:27 | `feature/webgpu-clean` | `e561462a6f` | mihon-release-apks-e561462a6f75a340b32f1301c9e1f69019a1c3a1 | 139356457 | `sha256:18b83570d2eadf1e186a77aa65e48e2efc5bdcaa831d3ba864b0e2e84036684f` | 2026-12-01 | solo histórico |
| 43 | 2026-11-01 06:27 | `feature/webgpu-clean` | `e561462a6f` | mihon-unminified-apks-e561462a6f75a340b32f1301c9e1f69019a1c3a1 | 218153336 | `sha256:5a266eb3d357a6412f3b3e85ac236396a354e8441f50b8175d9e0351fe47b709` | 2026-12-01 | solo histórico |
| 44 | 2026-11-01 07:22 | `feature/webgpu-clean` | `8b00e5b081` | mihon-release-apks-8b00e5b0814dbcc80bea54fcb10cb39b04c0a906 | 139358520 | `sha256:a20e9451deb494bb7232d636fc6c74cd8999f488f88531a285206bed77a8bf54` | 2026-12-01 | solo histórico |
| 44 | 2026-11-01 07:22 | `feature/webgpu-clean` | `8b00e5b081` | mihon-unminified-apks-8b00e5b0814dbcc80bea54fcb10cb39b04c0a906 | 218153532 | `sha256:cfe0c1eed7438e4f7cff8f12a66a9dd2de927b79262165ccd7391be36474fb15` | 2026-12-01 | solo histórico |
| 45 | 2026-11-01 07:57 | `feature/webgpu-clean` | `87ee0d303f` | mihon-release-apks-87ee0d303f7c03dcb4ba450f91947a33c867967f | 139353617 | `sha256:e91aa353fbd621a72fb984a5512261ad483372cf2769322b2cbc448f9784d8a8` | 2026-12-01 | solo histórico |
| 45 | 2026-11-01 07:57 | `feature/webgpu-clean` | `87ee0d303f` | mihon-unminified-apks-87ee0d303f7c03dcb4ba450f91947a33c867967f | 218155021 | `sha256:978d4150a8961874616e80dc7b957807c5f80211b40758bcaf75d5d7ea91c020` | 2026-12-01 | solo histórico |
| 46 | 2026-11-01 09:33 | `feature/webgpu-clean` | `bd0f4b4263` | mihon-release-apks-bd0f4b42638c7003115a04c69ae7044093c1397f | 139367322 | `sha256:8e39ffcf334342c4bbaf879ba3b75af9f09e04572ffd821e9506e9ff15a3abee` | 2026-12-01 | solo histórico |
| 46 | 2026-11-01 09:33 | `feature/webgpu-clean` | `bd0f4b4263` | mihon-unminified-apks-bd0f4b42638c7003115a04c69ae7044093c1397f | 218151640 | `sha256:f1ad8bafa7791a857ff8052deeda166ee359b593fd8a1df97aa23dc08b351264` | 2026-12-01 | solo histórico |
| 47 | 2026-11-01 14:39 | `feature/webgpu-clean` | `74a2db0ad0` | mihon-release-apks-74a2db0ad0f3bc5a3a9aeef1c799365635936c0f | 139362809 | `sha256:575ed9bf25f43fce8986d0cd22b78f6975fc4f022f955d6a431bec5e0471d479` | 2026-12-01 | solo histórico |
| 47 | 2026-11-01 14:39 | `feature/webgpu-clean` | `74a2db0ad0` | mihon-unminified-apks-74a2db0ad0f3bc5a3a9aeef1c799365635936c0f | 218157533 | `sha256:827d1eeacda8fbb702bfb3a7b3bef11d17c7962ead156330f259f7e7656bd8c4` | 2026-12-01 | solo histórico |
| 48 | 2026-11-01 15:54 | `feature/webgpu-clean` | `4c119f5826` | mihon-release-apks-4c119f5826b93b0984034dbf600a6985260f4615 | 139378258 | `sha256:ba47bc28b38dc06f8915eec93d8e94baa2398a3b4d847035d17b765fc396d1e5` | 2026-12-01 | solo histórico |
| 48 | 2026-11-01 15:54 | `feature/webgpu-clean` | `4c119f5826` | mihon-unminified-apks-4c119f5826b93b0984034dbf600a6985260f4615 | 218167907 | `sha256:2e16605b4d61defd52cfe42496fc0ccd62e6f0fa30a62156cc587bfcf056001f` | 2026-12-01 | solo histórico |
| 49 | 2026-11-01 16:23 | `feature/webgpu-clean` | `8c8f1466c2` | mihon-release-apks-8c8f1466c2b6a3b8f4374b00ddf426c83af9d72f | 139400323 | `sha256:56665fc420ecf9b46031db7e6e5729e15a09efc291e2913c37cd9cc378446b33` | 2026-12-01 | solo histórico |
| 49 | 2026-11-01 16:23 | `feature/webgpu-clean` | `8c8f1466c2` | mihon-unminified-apks-8c8f1466c2b6a3b8f4374b00ddf426c83af9d72f | 218195011 | `sha256:33fa4071088d53f471f46030ca044e23a80078d56d94523acc78c007b2b53ed1` | 2026-12-01 | solo histórico |
| 51 | 2026-11-01 16:58 | `feature/webgpu-clean` | `2f0c11b36f` | mihon-release-apks-2f0c11b36f414837d5ead4200eaf75e864c0d375 | 139420947 | `sha256:68c72a60530220551aa983b24e054a02ee91ef68650735c49f86b41978b35a08` | 2026-12-01 | solo histórico |
| 51 | 2026-11-01 16:58 | `feature/webgpu-clean` | `2f0c11b36f` | mihon-unminified-apks-2f0c11b36f414837d5ead4200eaf75e864c0d375 | 218245360 | `sha256:810538370e651ecd71a0f6244d15c65c2b5b581c01f4b0e01fbbf2ab5b551afd` | 2026-12-01 | solo histórico |
| 54 | 2026-11-01 17:54 | `feature/webgpu-clean` | `6bdbfa7de9` | mhscans-extension-apk-6bdbfa7de920c246166e3551cb8cc4d0654a04dd | 707017 | `sha256:4c2445b0436e01003ca71c24a2dd431f370272e711ae0f9df4c238a00a9a11db` | 2026-12-01 | solo histórico |
| 54 | 2026-11-01 17:54 | `feature/webgpu-clean` | `6bdbfa7de9` | mihon-release-apks-6bdbfa7de920c246166e3551cb8cc4d0654a04dd | 139421278 | `sha256:426c2e5dbd28a5d51327fe98d93e1ad0dbecace725c88ce846819486e0897c5e` | 2026-12-01 | solo histórico |
| 54 | 2026-11-01 17:54 | `feature/webgpu-clean` | `6bdbfa7de9` | mihon-unminified-apks-6bdbfa7de920c246166e3551cb8cc4d0654a04dd | 218245092 | `sha256:28b6954d4260f3378bd38b2c2ca5ed3eef4a3ae7ac4df8f9a2d4a60b3057c8bd` | 2026-12-01 | solo histórico |
| 55 | 2026-11-01 18:08 | `feature/webgpu-clean` | `37a8e6b300` | mhscans-extension-apk-37a8e6b300a572094c5032a5eddbaff85e7410c9 | 708501 | `sha256:d3cf7af5a3c9b67282408913c0936a93a50a42dbd54628766f08aac5d93359a8` | 2026-12-01 | solo histórico |
| 55 | 2026-11-01 18:08 | `feature/webgpu-clean` | `37a8e6b300` | mihon-release-apks-37a8e6b300a572094c5032a5eddbaff85e7410c9 | 139421004 | `sha256:abb86698e115c4f01ee34a8de4c3275ec9c669ee6eb8dc43394e48a51dc5756a` | 2026-12-01 | solo histórico |
| 55 | 2026-11-01 18:08 | `feature/webgpu-clean` | `37a8e6b300` | mihon-unminified-apks-37a8e6b300a572094c5032a5eddbaff85e7410c9 | 218244882 | `sha256:da860052089d68dfd27c07d833760c8ac5812dd45858ed2d6fa2ef09e3549312` | 2026-12-01 | solo histórico |
| 57 | 2026-11-01 19:56 | `feature/webgpu-clean` | `e755bde320` | emperorscan-extension-apk-e755bde32089e80112c9ebc29ad7e1e370568885 | 812905 | `sha256:347df64804b4fcb6f05d710ed560fb74413373bef6d3a121755416fb18fb8b77` | 2026-12-01 | solo histórico |
| 57 | 2026-11-01 19:56 | `feature/webgpu-clean` | `e755bde320` | mhscans-extension-apk-e755bde32089e80112c9ebc29ad7e1e370568885 | 708496 | `sha256:3463e71d9381cb1ede5b351a14595b7e3c327482f6930c052701cdb057643dbe` | 2026-12-01 | solo histórico |
| 57 | 2026-11-01 19:56 | `feature/webgpu-clean` | `e755bde320` | mihon-release-apks-e755bde32089e80112c9ebc29ad7e1e370568885 | 139421603 | `sha256:c3e075614122cf1ed767419af35c886605444312ef247d7cf93528db0d7090cd` | 2026-12-01 | solo histórico |
| 57 | 2026-11-01 19:56 | `feature/webgpu-clean` | `e755bde320` | mihon-unminified-apks-e755bde32089e80112c9ebc29ad7e1e370568885 | 218242878 | `sha256:bd8357b758a5307d824be70913fc4668da182b7524cb1c4d46eab51cb1d94231` | 2026-12-01 | solo histórico |
| 58 | 2026-11-01 20:24 | `feature/webgpu-clean` | `41c0520313` | emperorscan-extension-apk-41c052031326a3ea43e38e5f8d58c897894ded72 | 812803 | `sha256:8bfa7620729846257b164ce6da67e16b27145aa1378ae6d9e10331345418caea` | 2026-12-01 | solo histórico |
| 58 | 2026-11-01 20:24 | `feature/webgpu-clean` | `41c0520313` | mhscans-extension-apk-41c052031326a3ea43e38e5f8d58c897894ded72 | 708494 | `sha256:84f3a3e9f5508fef44cf3f298140de1fa809dbf489d20180215e970d3bb41bfa` | 2026-12-01 | solo histórico |
| 58 | 2026-11-01 20:24 | `feature/webgpu-clean` | `41c0520313` | mihon-release-apks-41c052031326a3ea43e38e5f8d58c897894ded72 | 139420837 | `sha256:519994597765b2b066675cf07458534554b3a5c9fa8f1d8242e1be5677d881f9` | 2026-12-01 | solo histórico |
| 58 | 2026-11-01 20:24 | `feature/webgpu-clean` | `41c0520313` | mihon-unminified-apks-41c052031326a3ea43e38e5f8d58c897894ded72 | 218244553 | `sha256:cb0c4cddb2997f1a78ecb91c9d3aa3ac88d65880245bdf42fe55fc300f623b1f` | 2026-12-01 | solo histórico |
| 59 | 2026-11-01 21:11 | `feature/webgpu-clean` | `f9462aa1fd` | emperorscan-extension-apk-f9462aa1fd2e88944c05f7ab98df667af3beaee8 | 813446 | `sha256:930651211c46028b84231b15948a991bc24f9605ee196e87f55a115f04162e6a` | 2026-12-01 | solo histórico |
| 59 | 2026-11-01 21:11 | `feature/webgpu-clean` | `f9462aa1fd` | mhscans-extension-apk-f9462aa1fd2e88944c05f7ab98df667af3beaee8 | 708501 | `sha256:31e5d21b4c1c7f22f01385168868a19bc333e9819afdeeb4322e081b75977142` | 2026-12-01 | solo histórico |
| 59 | 2026-11-01 21:11 | `feature/webgpu-clean` | `f9462aa1fd` | mihon-release-apks-f9462aa1fd2e88944c05f7ab98df667af3beaee8 | 139555640 | `sha256:87ca1187c3dd3a91c11ee6b863c7ff06f6e224c9c6a81829f501a74bdd4bae3e` | 2026-12-01 | solo histórico |
| 59 | 2026-11-01 21:11 | `feature/webgpu-clean` | `f9462aa1fd` | mihon-unminified-apks-f9462aa1fd2e88944c05f7ab98df667af3beaee8 | 218257185 | `sha256:9d7b941a0a454b5b46f4f263806d627bef1c748a0cfc9770421f741a344cab3e` | 2026-12-01 | solo histórico |
| 60 | 2026-11-01 22:15 | `feature/webgpu-clean` | `297a905f3d` | emperorscan-extension-apk-297a905f3d24570b53298a7668e97c9e67665621 | 813440 | `sha256:63d02cd5e59f36fb0e66c91f178cc6c8a73958f047aeefe0ef8e391297d0e40c` | 2026-12-01 | solo histórico |
| 60 | 2026-11-01 22:15 | `feature/webgpu-clean` | `297a905f3d` | mhscans-extension-apk-297a905f3d24570b53298a7668e97c9e67665621 | 708494 | `sha256:b6abbdb4bae2563be48420d96ea2d492687d0184b6ddfbf223b92799b1485645` | 2026-12-01 | solo histórico |
| 60 | 2026-11-01 22:15 | `feature/webgpu-clean` | `297a905f3d` | mihon-release-apks-297a905f3d24570b53298a7668e97c9e67665621 | 139551002 | `sha256:bf7b2e0908f38d193fc468a4171032120392d3bb8a179cb472fb362e6e713310` | 2026-12-01 | solo histórico |
| 60 | 2026-11-01 22:15 | `feature/webgpu-clean` | `297a905f3d` | mihon-unminified-apks-297a905f3d24570b53298a7668e97c9e67665621 | 218254909 | `sha256:94b62b068bdbee67c1a19cace08df63727e66de7459f06e5287f1441cbce5efa` | 2026-12-01 | solo histórico |
| 61 | 2026-11-01 23:08 | `feature/webgpu-clean` | `399a3fc855` | emperorscan-extension-apk-399a3fc8553464aa0c0c64dc2f4cb46da5e0f5fd | 813450 | `sha256:a28db54b619a36ae70e5e90187787edc15b98c1ebf000417f7d4d19f5973695f` | 2026-12-01 | solo histórico |
| 61 | 2026-11-01 23:08 | `feature/webgpu-clean` | `399a3fc855` | mhscans-extension-apk-399a3fc8553464aa0c0c64dc2f4cb46da5e0f5fd | 708497 | `sha256:8d36e57fd0e0cf8e0ea911d4bad8684dfa02c7703556c27551fb543e93e03701` | 2026-12-01 | solo histórico |
| 61 | 2026-11-01 23:08 | `feature/webgpu-clean` | `399a3fc855` | mihon-release-apks-399a3fc8553464aa0c0c64dc2f4cb46da5e0f5fd | 139553451 | `sha256:91615667d01272b685fc94e842bf60f960b8ad30c3979b94f57a48b86ccc6a8d` | 2026-12-01 | solo histórico |
| 61 | 2026-11-01 23:08 | `feature/webgpu-clean` | `399a3fc855` | mihon-unminified-apks-399a3fc8553464aa0c0c64dc2f4cb46da5e0f5fd | 218259757 | `sha256:6a793082636719554aa11b6e4917cea924dffbf3c53f7c2996e0f25444823c9b` | 2026-12-01 | solo histórico |
| 62 | 2026-11-02 01:14 | `feature/webgpu-clean` | `ec79888927` | emperorscan-extension-apk-ec79888927344fb45657b3df72b416997de9436e | 813449 | `sha256:81b8be31d46e9c15d4a5d30b265015ca752d896cc268cf458566d1a9546ad531` | 2026-12-02 | solo histórico |
| 62 | 2026-11-02 01:14 | `feature/webgpu-clean` | `ec79888927` | mhscans-extension-apk-ec79888927344fb45657b3df72b416997de9436e | 708497 | `sha256:8538c570074f4bd55d9638971aa4c8829a10b9084f1c80c5b2e093d6f710ab4b` | 2026-12-02 | solo histórico |
| 62 | 2026-11-02 01:14 | `feature/webgpu-clean` | `ec79888927` | mihon-release-apks-ec79888927344fb45657b3df72b416997de9436e | 139560316 | `sha256:95623d246625ab2711772cf300b4b4c60852673c888bb8f40fbb0f64c5d55c00` | 2026-12-02 | solo histórico |
| 62 | 2026-11-02 01:14 | `feature/webgpu-clean` | `ec79888927` | mihon-unminified-apks-ec79888927344fb45657b3df72b416997de9436e | 218263742 | `sha256:1489df3005e66e8f78549f338c6b3ff03f0e873b67f945ea46797611f5f4f60c` | 2026-12-02 | solo histórico |
| 63 | 2026-11-02 02:02 | `feature/webgpu-clean` | `729d6253d7` | emperorscan-extension-apk-729d6253d71c3d6297847c05be36f44581bbd88a | 813443 | `sha256:7501e42c2ddf2c3c75daaea4fc846e6ea51e4c7f8f6d6572f21509559591725c` | 2026-12-02 | solo histórico |
| 63 | 2026-11-02 02:02 | `feature/webgpu-clean` | `729d6253d7` | mhscans-extension-apk-729d6253d71c3d6297847c05be36f44581bbd88a | 708497 | `sha256:d533f5c2c157bbda22df53ec1a63880c21f01d82ff34b8751e22c8746eb56d80` | 2026-12-02 | solo histórico |
| 63 | 2026-11-02 02:02 | `feature/webgpu-clean` | `729d6253d7` | mihon-release-apks-729d6253d71c3d6297847c05be36f44581bbd88a | 139560258 | `sha256:cbb0ea2b4c1d8bfc46f022cec260438513552ccadcbe5bacb1311685e40f2819` | 2026-12-02 | solo histórico |
| 63 | 2026-11-02 02:02 | `feature/webgpu-clean` | `729d6253d7` | mihon-unminified-apks-729d6253d71c3d6297847c05be36f44581bbd88a | 218265672 | `sha256:36d5f8d45f9c36f2949c4368a8e3ab4bf8e7852c57181b148a3159df4d600d74` | 2026-12-02 | solo histórico |
| 64 | 2026-11-02 02:35 | `feature/webgpu-clean` | `f8420806ed` | emperorscan-extension-apk-f8420806edc9695b2a0cdf0dfbf60a88a6f95d64 | 813449 | `sha256:1ee0de64ebc6d5d6026598717d7cbf55164b5e67cef4f64b4259f2882f35be35` | 2026-12-02 | solo histórico |
| 64 | 2026-11-02 02:35 | `feature/webgpu-clean` | `f8420806ed` | mhscans-extension-apk-f8420806edc9695b2a0cdf0dfbf60a88a6f95d64 | 708500 | `sha256:567bfd1eb451ccccf99b3b106bffdf7826289c698d7069de8052dcc8f04a3ae8` | 2026-12-02 | solo histórico |
| 64 | 2026-11-02 02:35 | `feature/webgpu-clean` | `f8420806ed` | mihon-release-apks-f8420806edc9695b2a0cdf0dfbf60a88a6f95d64 | 139562625 | `sha256:593fa9ed0d9e863be8b493343320049979a2bfddbfa919635c72ebc190512a43` | 2026-12-02 | solo histórico |
| 64 | 2026-11-02 02:35 | `feature/webgpu-clean` | `f8420806ed` | mihon-unminified-apks-f8420806edc9695b2a0cdf0dfbf60a88a6f95d64 | 218264358 | `sha256:c1ae990783a115b4f5703a1b72425f9d0c1e7d1034eb416879823a4873c3e349` | 2026-12-02 | solo histórico |
| 65 | 2026-11-02 03:27 | `feature/webgpu-clean` | `bd84e7e02e` | emperorscan-extension-apk-bd84e7e02ebbcedcf14a390a69ee229f2bedeac5 | 813436 | `sha256:96f266f1fbddc1ad2d8a8f118fcee5e5a5b755b5d0c86fae0ae9ad45291f11cc` | 2026-12-02 | solo histórico |
| 65 | 2026-11-02 03:27 | `feature/webgpu-clean` | `bd84e7e02e` | mhscans-extension-apk-bd84e7e02ebbcedcf14a390a69ee229f2bedeac5 | 708497 | `sha256:116f5c458c4cba5ebc54e09f8dea7da307d0ebe4cd3f5e2466da3ea227448a04` | 2026-12-02 | solo histórico |
| 65 | 2026-11-02 03:27 | `feature/webgpu-clean` | `bd84e7e02e` | mihon-release-apks-bd84e7e02ebbcedcf14a390a69ee229f2bedeac5 | 139564879 | `sha256:2bb085c43c160bb6bf025b6fe5749c28d4bdb408c0d0e263aac8cb7b0deb0507` | 2026-12-02 | solo histórico |
| 65 | 2026-11-02 03:27 | `feature/webgpu-clean` | `bd84e7e02e` | mihon-unminified-apks-bd84e7e02ebbcedcf14a390a69ee229f2bedeac5 | 218275238 | `sha256:77aee4e25dcf74604147f90dd8279d20ab848acdd3284886f85ecac05eded9ae` | 2026-12-02 | solo histórico |
| 66 | 2026-11-02 17:06 | `feature/webgpu-clean` | `aa86a4390a` | emperorscan-extension-apk-aa86a4390a323b4a48f7a8060ffb5db345475176 | 813441 | `sha256:ba682a59306059372df6c521ec00f72ce41e7decbdf20a2775997b39a89ea643` | 2026-12-02 | solo histórico |
| 66 | 2026-11-02 17:06 | `feature/webgpu-clean` | `aa86a4390a` | mhscans-extension-apk-aa86a4390a323b4a48f7a8060ffb5db345475176 | 708498 | `sha256:6823936405be0664c79fafde0c376b68fd1a0a36da7f759028a6debe276d8587` | 2026-12-02 | solo histórico |
| 66 | 2026-11-02 17:06 | `feature/webgpu-clean` | `aa86a4390a` | mihon-release-apks-aa86a4390a323b4a48f7a8060ffb5db345475176 | 139564455 | `sha256:45de34e962c1fee5021acd71045f282fa3129306edba22df112eedffac9ab84c` | 2026-12-02 | solo histórico |
| 66 | 2026-11-02 17:06 | `feature/webgpu-clean` | `aa86a4390a` | mihon-unminified-apks-aa86a4390a323b4a48f7a8060ffb5db345475176 | 218273736 | `sha256:ef4878fa9a710218bc8938659116bb19ea34ce32469e478a4711802926609940` | 2026-12-02 | solo histórico |
| 67 | 2026-11-02 17:43 | `feature/webgpu-clean` | `efb98f1cd1` | emperorscan-extension-apk-efb98f1cd1581657c76515b879052f39a563d90e | 813447 | `sha256:ec7d2f331b70e612e6ac6cc51c94d6a6f3d31529df6d5e2485b05cb2c19e511a` | 2026-12-02 | solo histórico |
| 67 | 2026-11-02 17:43 | `feature/webgpu-clean` | `efb98f1cd1` | mhscans-extension-apk-efb98f1cd1581657c76515b879052f39a563d90e | 708499 | `sha256:838bfb2c030857bc75b96d8d255c3e1a20f52e011cd35ca9f1a440bc71e39f04` | 2026-12-02 | solo histórico |
| 67 | 2026-11-02 17:43 | `feature/webgpu-clean` | `efb98f1cd1` | mihon-release-apks-efb98f1cd1581657c76515b879052f39a563d90e | 139564206 | `sha256:22cdd4bc4cdf4d9383e568695215de312c99a029af4318c0688e85acb02ec1c0` | 2026-12-02 | solo histórico |
| 67 | 2026-11-02 17:43 | `feature/webgpu-clean` | `efb98f1cd1` | mihon-unminified-apks-efb98f1cd1581657c76515b879052f39a563d90e | 218275242 | `sha256:cd6c560cd01136f5d43b8ae31f826c709684101f0e4ad04bd69c1de51990a09d` | 2026-12-02 | solo histórico |
| 71 | 2026-11-02 19:54 | `main` | `af8a41dbed` | mihon-release-apks-af8a41dbed93edb02884fed80352bafd5d61c086 | 139208348 | `sha256:0d2a649c2f7aeef37d7e1168788f840b6121b099ed1ac661598d235f9e47fd4c` | 2026-12-02 | ✅ bytes verificados |
| 71 | 2026-11-02 19:54 | `main` | `af8a41dbed` | mihon-unminified-apks-af8a41dbed93edb02884fed80352bafd5d61c086 | 218028315 | `sha256:b76971b565576821bf5ba5f2c393a5f69757c67e09824f76c70e43d40eb1c934` | 2026-12-02 | solo histórico |
| 72 | 2026-11-02 20:05 | `main` | `b4a26187fc` | mihon-release-apks-b4a26187fc260840150a4a48ffddf40ae7e22191 | 139207395 | `sha256:bb5290b0400183b030db8f59b40b75582f46fa62230422a01171d4abbc4ea173` | 2026-12-02 | ✅ bytes verificados |
| 72 | 2026-11-02 20:05 | `main` | `b4a26187fc` | mihon-unminified-apks-b4a26187fc260840150a4a48ffddf40ae7e22191 | 218028420 | `sha256:9cb6bca15c8aed733c80070618eb165c6f11529815a011470613d1a9ab66109c` | 2026-12-02 | solo histórico |
| 74 | 2026-11-02 21:26 | `feature/webgpu-clean` | `bba3a3c5dc` | emperorscan-extension-apk-bba3a3c5dc602e1a422cda392b9b09e1a88b1015 | 813440 | `sha256:f9f9fc93d4fbac81541b83e39809b984d41146a4820acd335fbf930ef335f609` | 2026-12-02 | solo histórico |
| 74 | 2026-11-02 21:26 | `feature/webgpu-clean` | `bba3a3c5dc` | mhscans-extension-apk-bba3a3c5dc602e1a422cda392b9b09e1a88b1015 | 708495 | `sha256:c10b949d42e2f46cffb1c3770df603a10042c7ddbd1bc00ab219a3284ed8d4ae` | 2026-12-02 | solo histórico |
| 74 | 2026-11-02 21:26 | `feature/webgpu-clean` | `bba3a3c5dc` | mihon-release-apks-bba3a3c5dc602e1a422cda392b9b09e1a88b1015 | 139599021 | `sha256:67200199a6aa581d1456085da10fcac9fc860674be1f132f8318f3e9b8f03934` | 2026-12-02 | solo histórico |
| 74 | 2026-11-02 21:26 | `feature/webgpu-clean` | `bba3a3c5dc` | mihon-unminified-apks-bba3a3c5dc602e1a422cda392b9b09e1a88b1015 | 218562687 | `sha256:e4f527edf8e917e8e098417677e605781e0c1c9112350a9fb1fad7e7ca51e006` | 2026-12-02 | solo histórico |
| 75 | 2026-11-02 22:01 | `feature/webgpu-clean` | `051bb53ed7` | emperorscan-extension-apk-051bb53ed7070034f8607588a8a9ce72ce4e7f03 | 813450 | `sha256:9e9c767d29bfa70b453e2d5638a6b44cce7d20e630e8ba3b9c1ec19e221eb7b9` | 2026-12-02 | solo histórico |
| 75 | 2026-11-02 22:01 | `feature/webgpu-clean` | `051bb53ed7` | mhscans-extension-apk-051bb53ed7070034f8607588a8a9ce72ce4e7f03 | 708498 | `sha256:d0004c53da05eba0ec27bc96452f63778d4104ce6d08cca9a9d042da8d90a279` | 2026-12-02 | solo histórico |
| 75 | 2026-11-02 22:01 | `feature/webgpu-clean` | `051bb53ed7` | mihon-release-apks-051bb53ed7070034f8607588a8a9ce72ce4e7f03 | 139599558 | `sha256:50a041259b709f109c5c1ed93b37cc3bcd7a98e61f685a351881ea53d6a942a2` | 2026-12-02 | solo histórico |
| 75 | 2026-11-02 22:01 | `feature/webgpu-clean` | `051bb53ed7` | mihon-unminified-apks-051bb53ed7070034f8607588a8a9ce72ce4e7f03 | 218558643 | `sha256:5dded1eb2d11f58adcde7beab4821a39afbc825e60af13242e49f7fbf8d3bedf` | 2026-12-02 | solo histórico |
| 76 | 2026-11-02 22:59 | `feature/webgpu-clean` | `436811ae96` | emperorscan-extension-apk-436811ae96d8f20a2802e031c9e7415f54483e43 | 813453 | `sha256:004d609cc98202ac1d351399336ae6ceba284b883254ecb0112618588515d6aa` | 2026-12-02 | solo histórico |
| 76 | 2026-11-02 22:59 | `feature/webgpu-clean` | `436811ae96` | mhscans-extension-apk-436811ae96d8f20a2802e031c9e7415f54483e43 | 708494 | `sha256:5702f8afc1cec277a24d7f538b8cab273ac93f33734a928928da97b52ed640be` | 2026-12-02 | solo histórico |
| 76 | 2026-11-02 22:59 | `feature/webgpu-clean` | `436811ae96` | mihon-release-apks-436811ae96d8f20a2802e031c9e7415f54483e43 | 139598693 | `sha256:136309acfd17aa843b3659fe7e5788877493ecaba40601bbef198aab9648099e` | 2026-12-02 | solo histórico |
| 76 | 2026-11-02 22:59 | `feature/webgpu-clean` | `436811ae96` | mihon-unminified-apks-436811ae96d8f20a2802e031c9e7415f54483e43 | 218553896 | `sha256:343f3e88e7d4b5cd6ddc63898de9ef265309566d0350ae0cb548494f880befea` | 2026-12-02 | solo histórico |
| 78 | 2026-11-02 23:44 | `feature/webgpu-clean` | `33f5b2c41e` | emperorscan-extension-apk-33f5b2c41e4eb2a5aad183609f10c63636234e49 | 813442 | `sha256:9bbc01c056aecb11a3b03d1a0c34650f55a775f420abb3382d84f77794436ced` | 2026-12-02 | solo histórico |
| 78 | 2026-11-02 23:44 | `feature/webgpu-clean` | `33f5b2c41e` | mhscans-extension-apk-33f5b2c41e4eb2a5aad183609f10c63636234e49 | 708497 | `sha256:0483421ec9b28f2f29a348dfd10f166f23ff3adce37c897ec8a8e7cc81a573af` | 2026-12-02 | solo histórico |
| 78 | 2026-11-02 23:44 | `feature/webgpu-clean` | `33f5b2c41e` | mihon-release-apks-33f5b2c41e4eb2a5aad183609f10c63636234e49 | 139600810 | `sha256:b2cc90ed529162b7fdfad6190a58bb5d777b1cf2fdbb776528548e5dd1650018` | 2026-12-02 | solo histórico |
| 78 | 2026-11-02 23:44 | `feature/webgpu-clean` | `33f5b2c41e` | mihon-unminified-apks-33f5b2c41e4eb2a5aad183609f10c63636234e49 | 218574531 | `sha256:998ce4b1e6f0eb262d451aff07e78007995af048b42c712687cd2a9c5d9d97f0` | 2026-12-02 | solo histórico |
| 80 | 2026-11-03 01:54 | `feature/webgpu-clean` | `fffacf1e85` | emperorscan-extension-apk-fffacf1e85c5c1a6e93dfef3aeb5e339edd6f5d4 | 813448 | `sha256:ef0335994f4392ca74356a5bdb190f37f8d6869d2eb0cdea8dbc426d45a9d5f4` | 2026-12-03 | solo histórico |
| 80 | 2026-11-03 01:54 | `feature/webgpu-clean` | `fffacf1e85` | mhscans-extension-apk-fffacf1e85c5c1a6e93dfef3aeb5e339edd6f5d4 | 708496 | `sha256:48e7e10ca45f566d5eda87eb46b7db40e279cfd5eb80f4510a87141b60f99b30` | 2026-12-03 | solo histórico |
| 80 | 2026-11-03 01:54 | `feature/webgpu-clean` | `fffacf1e85` | mihon-release-apks-fffacf1e85c5c1a6e93dfef3aeb5e339edd6f5d4 | 139600972 | `sha256:6f6f58c9fb29cb44e1102326be070f9509bb6d13c2d25347506c33bb0b77d203` | 2026-12-03 | solo histórico |
| 80 | 2026-11-03 01:54 | `feature/webgpu-clean` | `fffacf1e85` | mihon-unminified-apks-fffacf1e85c5c1a6e93dfef3aeb5e339edd6f5d4 | 218575899 | `sha256:9bfe31a6987ba3b7870df3c2e2b1b3b27559784f499b5a031cdfafb349648452` | 2026-12-03 | solo histórico |
| 81 | 2026-11-03 03:01 | `feature/webgpu-clean` | `be845174e4` | emperorscan-extension-apk-be845174e41bf94f3cc6dec4f96905a80f092d3e | 813448 | `sha256:157fe4f23581eaba24f5c528dc301557884552db28f54bac2a184e7b025acc18` | 2026-12-03 | solo histórico |
| 81 | 2026-11-03 03:01 | `feature/webgpu-clean` | `be845174e4` | mhscans-extension-apk-be845174e41bf94f3cc6dec4f96905a80f092d3e | 708492 | `sha256:999886a3641a879686e0da07d06e5a47069b8274dc4cc73e206aa00aed4f535d` | 2026-12-03 | solo histórico |
| 81 | 2026-11-03 03:01 | `feature/webgpu-clean` | `be845174e4` | mihon-release-apks-be845174e41bf94f3cc6dec4f96905a80f092d3e | 139600719 | `sha256:de7d529a1b2a18d05f5475d1463f72b32ab834bcc796d31eda6b114e678e0533` | 2026-12-03 | solo histórico |
| 81 | 2026-11-03 03:01 | `feature/webgpu-clean` | `be845174e4` | mihon-unminified-apks-be845174e41bf94f3cc6dec4f96905a80f092d3e | 218576368 | `sha256:4d4cd32f521344981845e63afeeb066c5536a7e0327e29a94459f9ade3edc2c1` | 2026-12-03 | solo histórico |
| 82 | 2026-11-03 13:27 | `feature/webgpu-clean` | `65a8c653c9` | emperorscan-extension-apk-65a8c653c95cf79477abf92411f2f1f6880d71c7 | 813444 | `sha256:83bbbe04185244ab271fbd9e5df4742d0f8a0951c33e7d5c28a36986b27726c0` | 2026-12-03 | solo histórico |
| 82 | 2026-11-03 13:27 | `feature/webgpu-clean` | `65a8c653c9` | mhscans-extension-apk-65a8c653c95cf79477abf92411f2f1f6880d71c7 | 708499 | `sha256:92fad42c1c6273dd985157fb90054913ee3c363aad52b559d0fe598a03ec0eb8` | 2026-12-03 | solo histórico |
| 82 | 2026-11-03 13:27 | `feature/webgpu-clean` | `65a8c653c9` | mihon-release-apks-65a8c653c95cf79477abf92411f2f1f6880d71c7 | 139601082 | `sha256:4c9477e579954d8ec3ae32e31066127aa51650f49b73a5df17166e1ea111f859` | 2026-12-03 | solo histórico |
| 82 | 2026-11-03 13:27 | `feature/webgpu-clean` | `65a8c653c9` | mihon-unminified-apks-65a8c653c95cf79477abf92411f2f1f6880d71c7 | 218577525 | `sha256:a383d8b54f85ac31db3562270d7a6acf97a5d4918472ec42e4e5b066d360b5b9` | 2026-12-03 | solo histórico |
| 83 | 2026-11-03 14:12 | `feature/webgpu-clean` | `d1a776d44f` | emperorscan-extension-apk-d1a776d44f223ff8247fa2e38d6aecca7b13f92a | 813443 | `sha256:3fceef2e286fdf31c335a02f7feded5d683d201fe040f682ffe4dc3a580fe8c1` | 2026-12-03 | solo histórico |
| 83 | 2026-11-03 14:12 | `feature/webgpu-clean` | `d1a776d44f` | mhscans-extension-apk-d1a776d44f223ff8247fa2e38d6aecca7b13f92a | 708491 | `sha256:cb29bae948bab720ffb7317c72d3ad44026417c75c62a0231e87c6acf27b0950` | 2026-12-03 | solo histórico |
| 83 | 2026-11-03 14:12 | `feature/webgpu-clean` | `d1a776d44f` | mihon-release-apks-d1a776d44f223ff8247fa2e38d6aecca7b13f92a | 139601102 | `sha256:dffced516fb333e874dd18477afcce7d2f7058b4869c6ebf7de0adf9fa1951c3` | 2026-12-03 | solo histórico |
| 83 | 2026-11-03 14:12 | `feature/webgpu-clean` | `d1a776d44f` | mihon-unminified-apks-d1a776d44f223ff8247fa2e38d6aecca7b13f92a | 218578440 | `sha256:e6309534bb05c0f43476bdcdf459651e0032ffa6f63c00117ea590986d8f40d7` | 2026-12-03 | solo histórico |
| 84 | 2026-11-03 20:52 | `feature/webgpu-clean` | `5f82393d49` | emperorscan-extension-apk-5f82393d49e4f94b6c339efe5c52d14e69763e6f | 813437 | `sha256:f795d9ae49d92b5e26040eaa30f437a4b8ae347edc891f3572df248220205e4e` | 2026-12-03 | solo histórico |
| 84 | 2026-11-03 20:52 | `feature/webgpu-clean` | `5f82393d49` | mhscans-extension-apk-5f82393d49e4f94b6c339efe5c52d14e69763e6f | 708495 | `sha256:372263d0f400c2da9e1c7da6c251d1515e6ad7becd3d9e16c5c44838c6cbffac` | 2026-12-03 | solo histórico |
| 84 | 2026-11-03 20:52 | `feature/webgpu-clean` | `5f82393d49` | mihon-release-apks-5f82393d49e4f94b6c339efe5c52d14e69763e6f | 139627714 | `sha256:61d56cc444fd3c6cddad81d5608add2b8a0cf49e7f73bb53db08cf7a1ee38ac2` | 2026-12-03 | ✅ bytes verificados |
| 84 | 2026-11-03 20:52 | `feature/webgpu-clean` | `5f82393d49` | mihon-unminified-apks-5f82393d49e4f94b6c339efe5c52d14e69763e6f | 218619934 | `sha256:2706c92f47c18bcb8f7021146b12a19e6a640eb6b1c911847487b53e084ee952` | 2026-12-03 | solo histórico |
| 85 | 2026-11-03 21:16 | `feature/webgpu-clean` | `41a3c74dea` | emperorscan-extension-apk-41a3c74dea2209d264d0c79e8d675d42a3c9be8d | 813443 | `sha256:d109adc59144b0c391a7e0fb557796258e75c36cfd0da7c305bcedb3fc95e7a4` | 2026-12-03 | solo histórico |
| 85 | 2026-11-03 21:16 | `feature/webgpu-clean` | `41a3c74dea` | mhscans-extension-apk-41a3c74dea2209d264d0c79e8d675d42a3c9be8d | 708498 | `sha256:2e4998c9a5969ee8804539dd5a5caab96a3d33a331ffdbf954a8ee20667852c3` | 2026-12-03 | solo histórico |
| 85 | 2026-11-03 21:16 | `feature/webgpu-clean` | `41a3c74dea` | mihon-release-apks-41a3c74dea2209d264d0c79e8d675d42a3c9be8d | 139627761 | `sha256:5776bdaf0eb62a716b84dcc68b7ddc3968e1091119285877f6e458e722307334` | 2026-12-03 | solo histórico |
| 85 | 2026-11-03 21:16 | `feature/webgpu-clean` | `41a3c74dea` | mihon-unminified-apks-41a3c74dea2209d264d0c79e8d675d42a3c9be8d | 218620228 | `sha256:0e332af9283b8afd9ae5e7a8500a1f2562d8bd7011e8b4cfd2cfc78232716ce4` | 2026-12-03 | solo histórico |
| 86 | 2026-11-03 23:08 | `feature/webgpu-clean` | `69769b1ec7` | emperorscan-extension-apk-69769b1ec779408f37cf1cc5e2ac4714873eb1f1 | 813449 | `sha256:4d7ccc8bc7446dc93d5ae023a380a208127e5c65211fba67e5fa1226b4405785` | 2026-12-03 | solo histórico |
| 86 | 2026-11-03 23:08 | `feature/webgpu-clean` | `69769b1ec7` | mhscans-extension-apk-69769b1ec779408f37cf1cc5e2ac4714873eb1f1 | 708495 | `sha256:afcbfe9cbd849d280127efa275efb6ccb0411afc85e7aba8b1c0398524b3ddab` | 2026-12-03 | solo histórico |
| 86 | 2026-11-03 23:08 | `feature/webgpu-clean` | `69769b1ec7` | mihon-release-apks-69769b1ec779408f37cf1cc5e2ac4714873eb1f1 | 139637656 | `sha256:e36bec510efc2f03a2ebd9c0c3119681628b820a02bb00c3bf2324449355b883` | 2026-12-03 | ✅ bytes verificados |
| 86 | 2026-11-03 23:08 | `feature/webgpu-clean` | `69769b1ec7` | mihon-unminified-apks-69769b1ec779408f37cf1cc5e2ac4714873eb1f1 | 218663420 | `sha256:008a297642d9234bf5f3593a4584f527c2f756b8baef7167e23b6f4d339b4a4a` | 2026-12-03 | solo histórico |
| 87 | 2026-11-03 23:54 | `feature/webgpu-clean` | `4a00792411` | emperorscan-extension-apk-4a007924117e762fd6da38db38d2ab6e4c22b5cc | 813438 | `sha256:07366610dfd3609e45b6631a6baee053215ff667ff20bcdf94998ed9db62b03c` | 2026-12-03 | solo histórico |
| 87 | 2026-11-03 23:54 | `feature/webgpu-clean` | `4a00792411` | mhscans-extension-apk-4a007924117e762fd6da38db38d2ab6e4c22b5cc | 708500 | `sha256:992b7a4a37b2cfeb42212138222a8733389dbb7521ebaa90cc6aa13d0af02884` | 2026-12-03 | solo histórico |
| 87 | 2026-11-03 23:54 | `feature/webgpu-clean` | `4a00792411` | mihon-release-apks-4a007924117e762fd6da38db38d2ab6e4c22b5cc | 139637509 | `sha256:f219fb3cf249dcb3350651e54f42869bcf9a858f5a09de89905b8826842cdf7c` | 2026-12-04 | ✅ bytes verificados |
| 87 | 2026-11-03 23:54 | `feature/webgpu-clean` | `4a00792411` | mihon-unminified-apks-4a007924117e762fd6da38db38d2ab6e4c22b5cc | 218660375 | `sha256:6a8f109f83315aaba3a994b8561c822c74134898de491ea819a9d4b32ed14d66` | 2026-12-04 | solo histórico |
| 88 | 2026-11-04 00:27 | `feature/webgpu-clean` | `51d158c7e7` | emperorscan-extension-apk-51d158c7e7857a04600c175b87777fb8f63b0543 | 813438 | `sha256:c0a7f8d3e7eabd6fcbdfb7b28079f03bcf2df38c54511d81818cf9c0367c0dcc` | 2026-12-04 | solo histórico |
| 88 | 2026-11-04 00:27 | `feature/webgpu-clean` | `51d158c7e7` | mhscans-extension-apk-51d158c7e7857a04600c175b87777fb8f63b0543 | 708496 | `sha256:5272bd7093c36935d5a452ca3dc409274c476143ac963ef6b84e99f2deba9ba1` | 2026-12-04 | solo histórico |
| 88 | 2026-11-04 00:27 | `feature/webgpu-clean` | `51d158c7e7` | mihon-release-apks-51d158c7e7857a04600c175b87777fb8f63b0543 | 139637219 | `sha256:e4b9c68175d827eef87e99545656778c65c21d8f482fb72f74bf86ea676b9cca` | 2026-12-04 | ✅ bytes verificados |
| 88 | 2026-11-04 00:27 | `feature/webgpu-clean` | `51d158c7e7` | mihon-unminified-apks-51d158c7e7857a04600c175b87777fb8f63b0543 | 218663965 | `sha256:4552f038f4310a77aeacb729143e36602c125156038fb5bd98ea3feecf48bde3` | 2026-12-04 | solo histórico |
| 90 | 2026-11-04 03:36 | `feature/webgpu-clean` | `d37ffb3de8` | emperorscan-extension-apk-d37ffb3de88612a141dd5cc6548ad43d552e04ad | 813451 | `sha256:46933676a357d6d48fe2e6dad440c754ee05a08c44e8c8c0db8f7186b1a8f358` | 2026-12-04 | solo histórico |
| 90 | 2026-11-04 03:36 | `feature/webgpu-clean` | `d37ffb3de8` | mhscans-extension-apk-d37ffb3de88612a141dd5cc6548ad43d552e04ad | 708498 | `sha256:604668c6145f8e56febc5b16aa604a00c75cece50f90d193a3a846cd69aecd71` | 2026-12-04 | solo histórico |
| 90 | 2026-11-04 03:36 | `feature/webgpu-clean` | `d37ffb3de8` | mihon-release-apks-d37ffb3de88612a141dd5cc6548ad43d552e04ad | 139657059 | `sha256:e8f0a177726743a2d3cad9af7753db345c1bf4c119caf16340bfeccc40c03095` | 2026-12-04 | ✅ bytes verificados |
| 90 | 2026-11-04 03:36 | `feature/webgpu-clean` | `d37ffb3de8` | mihon-unminified-apks-d37ffb3de88612a141dd5cc6548ad43d552e04ad | 218676890 | `sha256:d1875074a0612d4dfb26d759a863c944999840741af6aca57e05d7b10407221e` | 2026-12-04 | solo histórico |
| 91 | 2026-11-04 04:02 | `feature/webgpu-clean` | `58416b863c` | emperorscan-extension-apk-58416b863c239c39af1e554a1bf3057f35dd038a | 813448 | `sha256:9c3bf4261f95961d021fb62d4cfa91189e3aa0f365f1f856eea6c790b95544e6` | 2026-12-04 | solo histórico |
| 91 | 2026-11-04 04:02 | `feature/webgpu-clean` | `58416b863c` | mhscans-extension-apk-58416b863c239c39af1e554a1bf3057f35dd038a | 708495 | `sha256:2a0b4a3be2bf4f746bbb08de37ebdca076466f74de5c439e0cc3130a74f37754` | 2026-12-04 | solo histórico |
| 91 | 2026-11-04 04:02 | `feature/webgpu-clean` | `58416b863c` | mihon-release-apks-58416b863c239c39af1e554a1bf3057f35dd038a | 139657320 | `sha256:20863d2b652fe537e7c76cff6ac6c138ef6dbd1af54ace6e37b8f0430560f0d2` | 2026-12-04 | ✅ bytes verificados |
| 91 | 2026-11-04 04:02 | `feature/webgpu-clean` | `58416b863c` | mihon-unminified-apks-58416b863c239c39af1e554a1bf3057f35dd038a | 218675816 | `sha256:6add4cbdc02aa8393f00d391dbd67ff7774f09f99e0bc4b811f6f3e126a99edb` | 2026-12-04 | solo histórico |
| 92 | 2026-11-04 08:38 | `feature/webgpu-clean` | `1a39b744d9` | emperorscan-extension-apk-1a39b744d9400d812285cb6d56a68855088af5c5 | 813449 | `sha256:a51624f9e44f037db2077ef8c064f40503a26860a7b3c43c28a153e3ee231c3e` | 2026-12-04 | solo histórico |
| 92 | 2026-11-04 08:38 | `feature/webgpu-clean` | `1a39b744d9` | mhscans-extension-apk-1a39b744d9400d812285cb6d56a68855088af5c5 | 708500 | `sha256:d81e30ecfe50a43b643b524eb68e62f1faa2cad8a217d4418c8fe8cd1df810d7` | 2026-12-04 | solo histórico |
| 92 | 2026-11-04 08:38 | `feature/webgpu-clean` | `1a39b744d9` | mihon-release-apks-1a39b744d9400d812285cb6d56a68855088af5c5 | 139653197 | `sha256:6f79e7813543d1ece52d1aedc50d9a8f1b46915ffe98cbe9581ea242c6ebcd6e` | 2026-12-04 | ✅ bytes verificados |
| 92 | 2026-11-04 08:38 | `feature/webgpu-clean` | `1a39b744d9` | mihon-unminified-apks-1a39b744d9400d812285cb6d56a68855088af5c5 | 218684227 | `sha256:1a78413c7b0354ba5e9e2ab088dd8b70e7d0be3b8098166bcbfeb82767d2d542` | 2026-12-04 | solo histórico |
| 93 | 2026-11-04 08:56 | `feature/webgpu-clean` | `9ac6043f49` | emperorscan-extension-apk-9ac6043f49a33d04babb5a8ba018d224d169df59 | 813451 | `sha256:f99c50fa7f4b23875c34450bb40f709c62ffca2582e40166026897e14883ab52` | 2026-12-04 | solo histórico |
| 93 | 2026-11-04 08:56 | `feature/webgpu-clean` | `9ac6043f49` | mhscans-extension-apk-9ac6043f49a33d04babb5a8ba018d224d169df59 | 708501 | `sha256:68b77dfa68317f9f558922e42bc2e324cfbe3864db96095a59da337c71ad0f2f` | 2026-12-04 | solo histórico |
| 93 | 2026-11-04 08:56 | `feature/webgpu-clean` | `9ac6043f49` | mihon-release-apks-9ac6043f49a33d04babb5a8ba018d224d169df59 | 139652825 | `sha256:a4e1a7efe33f9afb98e4ba301175b21f586e437ea76dd50aae6542a7161af5ec` | 2026-12-04 | ✅ bytes verificados |
| 93 | 2026-11-04 08:56 | `feature/webgpu-clean` | `9ac6043f49` | mihon-unminified-apks-9ac6043f49a33d04babb5a8ba018d224d169df59 | 218686497 | `sha256:6f2a842dd4494b02c126fd5d5aed392d841bd74068fd7442341716220a491ac7` | 2026-12-04 | solo histórico |
| 94 | 2026-11-04 09:50 | `feature/webgpu-clean` | `b681b70fe5` | emperorscan-extension-apk-b681b70fe5d6f7896a4e36bc3c068f09afa9e89c | 813443 | `sha256:2cadf7a0ea948fe7ad550d48cb649eaeb3c18132136c643fbb8a13e4f5cce306` | 2026-12-04 | solo histórico |
| 94 | 2026-11-04 09:50 | `feature/webgpu-clean` | `b681b70fe5` | mhscans-extension-apk-b681b70fe5d6f7896a4e36bc3c068f09afa9e89c | 708495 | `sha256:7f6374f8381694801d8be8b8711829a4ed6e0e789088c256164e3e0354673afe` | 2026-12-04 | solo histórico |
| 94 | 2026-11-04 09:50 | `feature/webgpu-clean` | `b681b70fe5` | mihon-release-apks-b681b70fe5d6f7896a4e36bc3c068f09afa9e89c | 139662002 | `sha256:ae415833423ca764e4947bad4b91642a5afd089fe9c792e33f7048783ccf3181` | 2026-12-04 | ✅ bytes verificados |
| 94 | 2026-11-04 09:50 | `feature/webgpu-clean` | `b681b70fe5` | mihon-unminified-apks-b681b70fe5d6f7896a4e36bc3c068f09afa9e89c | 218687493 | `sha256:3711d5142fd9988683af563f78548a2ea865116d187e2bc2828f5c95b17eda3d` | 2026-12-04 | solo histórico |
| 95 | 2026-11-04 10:15 | `feature/webgpu-clean` | `350a7dc442` | emperorscan-extension-apk-350a7dc44276c5e398d44a925a9d222c4b7208cf | 813450 | `sha256:0170c35414962ed5da9b35aca4817d37242bb7a5cb157dfd056d459d64847e05` | 2026-12-04 | solo histórico |
| 95 | 2026-11-04 10:15 | `feature/webgpu-clean` | `350a7dc442` | mhscans-extension-apk-350a7dc44276c5e398d44a925a9d222c4b7208cf | 708497 | `sha256:f4862d3d1ea7e0418e8fabfe97451af61858e3d754bf32c9b3b57c4e11669594` | 2026-12-04 | solo histórico |
| 95 | 2026-11-04 10:15 | `feature/webgpu-clean` | `350a7dc442` | mihon-release-apks-350a7dc44276c5e398d44a925a9d222c4b7208cf | 139681913 | `sha256:52f86fdd9c1132a4b242f7e71948ee8748472b5ba5aee56130c876532287d571` | 2026-12-04 | ✅ bytes verificados |
| 95 | 2026-11-04 10:15 | `feature/webgpu-clean` | `350a7dc442` | mihon-unminified-apks-350a7dc44276c5e398d44a925a9d222c4b7208cf | 218706185 | `sha256:edf025e0ef636824fb017e7665f4e2e2c720e64f90f6f5be0f55371148e96442` | 2026-12-04 | solo histórico |
| 96 | 2026-11-04 10:26 | `feature/webgpu-clean` | `1284d6b9e3` | emperorscan-extension-apk-1284d6b9e30ad2c0c8fe333037ec117f9cd8992d | 813450 | `sha256:0eeb9c781cb29c0342ea5214f9da37fd43ae57799a0210edd5d6a15ca3b9beba` | 2026-12-04 | solo histórico |
| 96 | 2026-11-04 10:26 | `feature/webgpu-clean` | `1284d6b9e3` | mhscans-extension-apk-1284d6b9e30ad2c0c8fe333037ec117f9cd8992d | 708497 | `sha256:31dcacede8e1ea5769a8bcc5ad5a9c986cf14d1a6ac962c8fd6cec6817c88187` | 2026-12-04 | solo histórico |
| 96 | 2026-11-04 10:26 | `feature/webgpu-clean` | `1284d6b9e3` | mihon-release-apks-1284d6b9e30ad2c0c8fe333037ec117f9cd8992d | 139681974 | `sha256:c8b117ce2a09655eacb30bc1b6aab7dd65ebd04881343f3058a89050530af815` | 2026-12-04 | ✅ bytes verificados |
| 96 | 2026-11-04 10:26 | `feature/webgpu-clean` | `1284d6b9e3` | mihon-unminified-apks-1284d6b9e30ad2c0c8fe333037ec117f9cd8992d | 218703205 | `sha256:a0552631c2b51d58a18328f5d09448e3e63720bc9fdae17b9afae7bfff979ef5` | 2026-12-04 | solo histórico |
| 97 | 2026-11-04 13:07 | `feature/webgpu-clean` | `dd8d4d830e` | emperorscan-extension-apk-dd8d4d830efe761db0569f68ab6abd234e08cdb4 | 813451 | `sha256:9365eb87c17bbfde225480b0575848d4fe922185ff9dcbb9539b31a18f57b445` | 2026-12-04 | solo histórico |
| 97 | 2026-11-04 13:07 | `feature/webgpu-clean` | `dd8d4d830e` | mhscans-extension-apk-dd8d4d830efe761db0569f68ab6abd234e08cdb4 | 708501 | `sha256:b9eb9597376feb130e63fda9f76abd5179bd0e18fff5ed297486e8f578f4cb49` | 2026-12-04 | solo histórico |
| 97 | 2026-11-04 13:07 | `feature/webgpu-clean` | `dd8d4d830e` | mihon-release-apks-dd8d4d830efe761db0569f68ab6abd234e08cdb4 | 139682232 | `sha256:14592114794ff40a1fa90fe853c53f4026ad269831fc193b4cfa4a6dcdd9c46c` | 2026-12-04 | ✅ bytes verificados |
| 97 | 2026-11-04 13:07 | `feature/webgpu-clean` | `dd8d4d830e` | mihon-unminified-apks-dd8d4d830efe761db0569f68ab6abd234e08cdb4 | 218707102 | `sha256:ff49d7a1a02f479ee3f8776579f2fdbf302f39efffadcf6c53d5fb1ddb62b8e0` | 2026-12-04 | solo histórico |
| 98 | 2026-11-04 13:24 | `feature/webgpu-clean` | `275bd50f02` | emperorscan-extension-apk-275bd50f02dfcb06b02758327eb59a98326053bf | 813446 | `sha256:3c14e59e3bc790c95eb56afe708a6feb24f2279ee187d67b8769d3cff083fdca` | 2026-12-04 | solo histórico |
| 98 | 2026-11-04 13:24 | `feature/webgpu-clean` | `275bd50f02` | mhscans-extension-apk-275bd50f02dfcb06b02758327eb59a98326053bf | 708496 | `sha256:90fcf43af2d6dddb0c8d2aa945ac89b44b6a52076e61d35ed45ef2570fcf6bc4` | 2026-12-04 | solo histórico |
| 98 | 2026-11-04 13:24 | `feature/webgpu-clean` | `275bd50f02` | mihon-release-apks-275bd50f02dfcb06b02758327eb59a98326053bf | 139682397 | `sha256:de707dfeb498a3d94ec532f64fd9e49f01f6f45226019d58d279d2a8660fdf60` | 2026-12-04 | ✅ bytes verificados |
| 98 | 2026-11-04 13:24 | `feature/webgpu-clean` | `275bd50f02` | mihon-unminified-apks-275bd50f02dfcb06b02758327eb59a98326053bf | 218706289 | `sha256:967e43ea10262508c93fcff0734ddf4e7b7bf346d926afa66ac41cf633be0d06` | 2026-12-04 | solo histórico |
| 99 | 2026-11-05 17:03 | `feature/webgpu-clean` | `508f0c469e` | mihon-release-apks-508f0c469e7986b5c36e067b131c09c1a8c4adeb | 139691402 | `sha256:0aa366c409dbc371c1bd2603d76581b44fe35e344e58efa89c12053041a184c1` | 2026-12-05 | solo histórico |
| 99 | 2026-11-05 17:03 | `feature/webgpu-clean` | `508f0c469e` | mihon-unminified-apks-508f0c469e7986b5c36e067b131c09c1a8c4adeb | 218728710 | `sha256:1db8f1f3332be2ab8f2ae3e76dc9f20297130fa75d3b5e90b6ee04c806aa4454` | 2026-12-05 | solo histórico |
| 100 | 2026-11-05 17:18 | `feature/webgpu-clean` | `8ffcbb9680` | emperorscan-extension-apk-8ffcbb96808988cb5675d0576fcdcbb31f2c798c | 813444 | `sha256:797b8ce88678986f362fb2a2d0a5278706aa012c0e0d5e210d802ccb71775245` | 2026-12-05 | solo histórico |
| 100 | 2026-11-05 17:18 | `feature/webgpu-clean` | `8ffcbb9680` | mhscans-extension-apk-8ffcbb96808988cb5675d0576fcdcbb31f2c798c | 708493 | `sha256:faceaaa6582e77a9945ab0fdbf4da8954072f4c223884d00a3863caf6ae535ef` | 2026-12-05 | solo histórico |
| 100 | 2026-11-05 17:18 | `feature/webgpu-clean` | `8ffcbb9680` | mihon-release-apks-8ffcbb96808988cb5675d0576fcdcbb31f2c798c | 139691170 | `sha256:24c98c006e8ea81736769f5e60d1e2b0ec9d4b5a7d502c66524d7a623725871c` | 2026-12-05 | solo histórico |
| 100 | 2026-11-05 17:18 | `feature/webgpu-clean` | `8ffcbb9680` | mihon-unminified-apks-8ffcbb96808988cb5675d0576fcdcbb31f2c798c | 218727475 | `sha256:7f4996cb06406dc95953f5058bcf1f0c687a6bdf391ad2ce43aa1ab29031a81a` | 2026-12-05 | solo histórico |
| 101 | 2026-11-05 17:31 | `feature/webgpu-clean` | `978c710c9f` | mihon-release-apks-978c710c9f129f7d44ad2f3876ba3d9583b31ad7 | 139691355 | `sha256:3c246cb3c12d3ccdba66bf337b2e74b90cc7aa0ebf70c36310992564952509f4` | 2026-12-05 | ✅ bytes verificados |
| 101 | 2026-11-05 17:31 | `feature/webgpu-clean` | `978c710c9f` | mihon-unminified-apks-978c710c9f129f7d44ad2f3876ba3d9583b31ad7 | 218725622 | `sha256:38a63c29f04a46fd2b61f5782d97d54def1d2bbae50ee7beda712aa50172758e` | 2026-12-05 | solo histórico |
| 103 | 2026-11-06 06:41 | `feature/webgpu-clean` | `b9818fae13` | emperorscan-extension-apk-b9818fae13293614593341167018ea1ef0f87417 | 813534 | `sha256:d0ee2f448eb2e04d692c0164670e2da1d5ef5181401a6d8ba4e6c50b80fe9805` | 2026-12-06 | solo histórico |
| 103 | 2026-11-06 06:41 | `feature/webgpu-clean` | `b9818fae13` | mhscans-extension-apk-b9818fae13293614593341167018ea1ef0f87417 | 708590 | `sha256:e2254643948b7d98a16f572e07572849f4837d0a158e03fff2a6bb2d7a327099` | 2026-12-06 | solo histórico |
| 103 | 2026-11-06 06:41 | `feature/webgpu-clean` | `b9818fae13` | mihon-release-apks-b9818fae13293614593341167018ea1ef0f87417 | 139690872 | `sha256:0a9ac66297e80de5cc2f2b5b06375eee146add45df7eb8bfc1ab7be81cc29ee1` | 2026-12-06 | solo histórico |
| 103 | 2026-11-06 06:41 | `feature/webgpu-clean` | `b9818fae13` | mihon-unminified-apks-b9818fae13293614593341167018ea1ef0f87417 | 218721880 | `sha256:2f0b9b0691ebe350b6e1a5c884ea7979ad56f27c42c75ce3fc7dfba00a50d13b` | 2026-12-06 | solo histórico |
| 104 | 2026-11-06 07:05 | `feature/webgpu-clean` | `d1d8a4b823` | mihon-release-apks-d1d8a4b82377d78d6c73500ad5a5ae0de7437070 | 139689022 | `sha256:706c4e5ed9c77e6c77b0cf1453fd8cde36221dfcc81f6ef1220cef3d39b9210a` | 2026-12-06 | solo histórico |
| 104 | 2026-11-06 07:05 | `feature/webgpu-clean` | `d1d8a4b823` | mihon-unminified-apks-d1d8a4b82377d78d6c73500ad5a5ae0de7437070 | 218718895 | `sha256:4f5cacd4112b245985dc5ccd16bffca3e6785b8f81af2c46a29929793d566fab` | 2026-12-06 | solo histórico |
| 105 | 2026-11-06 17:52 | `feature/webgpu-clean` | `9f904c7182` | mihon-release-apks-9f904c71828cfce87999f13fe103f5cdeebdcb50 | 139688741 | `sha256:8ebbfd08b84aea12c2822a6d5185b45533281b845e19ff4ed99ccda1511dda1b` | 2026-12-06 | solo histórico |
| 105 | 2026-11-06 17:52 | `feature/webgpu-clean` | `9f904c7182` | mihon-unminified-apks-9f904c71828cfce87999f13fe103f5cdeebdcb50 | 218726670 | `sha256:825c04c55edd23a5eb269a3806dff17abbeed71a21acce639de83eea80d60886` | 2026-12-06 | solo histórico |
| 106 | 2026-11-06 18:40 | `feature/webgpu-clean` | `a5237598b4` | mihon-release-apks-a5237598b4c306111cc7555eee8f2b964ffd523b | 131426750 | `sha256:1c7dc1b35daa9c0cc2e8dd5ce386d82a9bd26ac41a029b62e26b5d845912b4c1` | 2026-12-06 | solo histórico |
| 106 | 2026-11-06 18:40 | `feature/webgpu-clean` | `a5237598b4` | mihon-unminified-apks-a5237598b4c306111cc7555eee8f2b964ffd523b | 210469153 | `sha256:2c755e97ec2e7800c54b797daecc830d9b5b8018bb445fd1c1d4cb63470248e0` | 2026-12-06 | solo histórico |
| 108 | 2026-11-06 18:56 | `feature/webgpu-clean` | `2354dfc61d` | mihon-release-apks-2354dfc61df7f4bd8279f43f3cdb4a33d541ed98 | 131426846 | `sha256:e5e3436c01ebea2ab98fb0db5f706f32e2b2d5ed86ce7da7ffdbb1b539dfe830` | 2026-12-06 | solo histórico |
| 108 | 2026-11-06 18:56 | `feature/webgpu-clean` | `2354dfc61d` | mihon-unminified-apks-2354dfc61df7f4bd8279f43f3cdb4a33d541ed98 | 210470438 | `sha256:a1c02b68b4ee136093fab9cc53567d220996023114ca5029ac82eebd048310c4` | 2026-12-06 | solo histórico |
| 109 | 2026-11-06 21:30 | `feature/webgpu-clean` | `92a71bd0e0` | mihon-release-apks-92a71bd0e0ae67e660b14081f8575d04f6f093d1 | 131425872 | `sha256:cc067ef9f77277d96e9be90f67982d4846587a8dc6e5f91826ef3911128ad6be` | 2026-12-06 | solo histórico |
| 109 | 2026-11-06 21:30 | `feature/webgpu-clean` | `92a71bd0e0` | mihon-unminified-apks-92a71bd0e0ae67e660b14081f8575d04f6f093d1 | 210467727 | `sha256:07bd38a7f4bd1d6a77054cc493c049de6a546d1864ccf2a51d8ca04f62ba05e2` | 2026-12-06 | solo histórico |
| 110 | 2026-11-06 21:43 | `feature/webgpu-clean` | `b0119da5ee` | mihon-release-apks-b0119da5eeb279ffc37d967d9b0705e40fab79b8 | 131425372 | `sha256:1fc657b75d18c61b09e854b23d5270f53f70fe26a3ff34d7a88223e1da4999e7` | 2026-12-06 | solo histórico |
| 110 | 2026-11-06 21:43 | `feature/webgpu-clean` | `b0119da5ee` | mihon-unminified-apks-b0119da5eeb279ffc37d967d9b0705e40fab79b8 | 210467056 | `sha256:4d23f93bd3054f10d6ffb09fad5d39d1a9967e3618f545231d6bece080638be2` | 2026-12-06 | solo histórico |
| 111 | 2026-11-06 22:21 | `feature/webgpu-clean` | `c0fe90f258` | mihon-release-apks-c0fe90f25812b667a3f98fcb825c25450638e3c3 | 131426467 | `sha256:5cf0a1a0f6b67fe7da5a8ffbc69c0f47ced464ab950f99206dbedadb555acc6d` | 2026-12-06 | solo histórico |
| 111 | 2026-11-06 22:21 | `feature/webgpu-clean` | `c0fe90f258` | mihon-unminified-apks-c0fe90f25812b667a3f98fcb825c25450638e3c3 | 210466336 | `sha256:daf2aa92d5e96fb20d016b3d7c50f8b4580fd23d45e6ba7ec2b3c2e02023547c` | 2026-12-06 | solo histórico |
| 113 | 2026-11-10 11:36 | `feature/webgpu-clean` | `14ec98072a` | mihon-release-apks-14ec98072af4dee9f3fc599762363365363d5f04 | 131426028 | `sha256:2f287f90a00d348de5619f1abae00494131829968b93e65e1ec0eac1184b8351` | 2026-12-10 | solo histórico |
| 113 | 2026-11-10 11:36 | `feature/webgpu-clean` | `14ec98072a` | mihon-unminified-apks-14ec98072af4dee9f3fc599762363365363d5f04 | 210467569 | `sha256:5df886bf63d2980ef3bab41eca54dda1e447f739503b28ff0fe7a7b40231dc5e` | 2026-12-10 | solo histórico |
| 114 | 2026-11-11 14:42 | `feature/webgpu-clean` | `a9bbaa7ad0` | mihon-release-apks-a9bbaa7ad06f0b0c381eb462c31190ac85c92937 | 131426913 | `sha256:4dc12b52ef94e098de1f97939c80910f758c41a00b0b50f3177d2cb2c4d99e22` | 2026-12-11 | solo histórico |
| 114 | 2026-11-11 14:42 | `feature/webgpu-clean` | `a9bbaa7ad0` | mihon-unminified-apks-a9bbaa7ad06f0b0c381eb462c31190ac85c92937 | 210468630 | `sha256:3020abb6c688c9176c31370051ce39a100df5e2e79e4cc177c00dddfcd5d346f` | 2026-12-11 | solo histórico |
| 116 | 2026-11-11 15:02 | `feature/webgpu-clean` | `70e30ab906` | mihon-release-apks-70e30ab906ea37a6e19845fcf3f1c53e3b42197c | 131427902 | `sha256:86e62313493609d3f8e28f3553751044b37febd3511e35b93541b9018e35d68e` | 2026-12-11 | solo histórico |
| 116 | 2026-11-11 15:02 | `feature/webgpu-clean` | `70e30ab906` | mihon-unminified-apks-70e30ab906ea37a6e19845fcf3f1c53e3b42197c | 210467972 | `sha256:9b992cff9790a684ffec18b0354f63a1d644c7fd163c2349d5e43475883ab4b2` | 2026-12-11 | solo histórico |
| 117 | 2026-11-11 18:41 | `feature/webgpu-clean` | `e3ef56c95b` | mihon-release-apks-e3ef56c95b05037665591143abe23a63dd3e8deb | 131448529 | `sha256:1958b5e78a9792ad6fb4daccc2e832f582db435fe3c84faa5f30fb60c514a53e` | 2026-12-11 | solo histórico |
| 117 | 2026-11-11 18:41 | `feature/webgpu-clean` | `e3ef56c95b` | mihon-unminified-apks-e3ef56c95b05037665591143abe23a63dd3e8deb | 210528804 | `sha256:e03722468ca679b4cdbffe7c2b38d85baeb2f1c6761e6802aed1da22171da953` | 2026-12-11 | solo histórico |
| 118 | 2026-11-11 18:52 | `feature/webgpu-clean` | `d00e2a0e54` | mihon-release-apks-d00e2a0e545793ea006bb10ea235eac438ae6884 | 131448562 | `sha256:3cecd4e14c4bcd121654bf5d0c399bfad2a4677e5d7d3df100463feeeb55c316` | 2026-12-11 | solo histórico |
| 118 | 2026-11-11 18:52 | `feature/webgpu-clean` | `d00e2a0e54` | mihon-unminified-apks-d00e2a0e545793ea006bb10ea235eac438ae6884 | 210528151 | `sha256:cd6c084acfffbb9827c4f7c628c32f7dbc2ec9171dee090e773c5fa34c7fc4a7` | 2026-12-11 | solo histórico |
| 120 | 2026-11-11 19:46 | `feature/webgpu-clean` | `85ffc0d0a0` | mihon-release-apks-85ffc0d0a01b3e2e4c7013799ca5af722479c616 | 131505818 | `sha256:290749fac96ed2d479ecce20fa851ef1f1dad8d1d54cef2f69bcb03dcc11e2f6` | 2026-12-11 | solo histórico |
| 120 | 2026-11-11 19:46 | `feature/webgpu-clean` | `85ffc0d0a0` | mihon-unminified-apks-85ffc0d0a01b3e2e4c7013799ca5af722479c616 | 210563556 | `sha256:766a4de317af24698db9530f4eb70d45fc627767dcd25981a158cc0bed3d9158` | 2026-12-11 | solo histórico |
| 121 | 2026-11-11 20:05 | `feature/webgpu-clean` | `f0977d725f` | mihon-release-apks-f0977d725ff3e9dbad4f599d2a244b96a42600cc | 131506188 | `sha256:b4f5c18e785d668bce664223d4a42de00314a71aaffbbd530665082ea77a38f0` | 2026-12-11 | solo histórico |
| 121 | 2026-11-11 20:05 | `feature/webgpu-clean` | `f0977d725f` | mihon-unminified-apks-f0977d725ff3e9dbad4f599d2a244b96a42600cc | 210562440 | `sha256:9b82a303cd5fb0bbfc24f2fda34832cc33f8708f8609fef33baf11b5a7915af6` | 2026-12-11 | solo histórico |
| 123 | 2026-11-11 23:22 | `fix/review-2026-11-12` | `5d95ba5505` | mihon-release-apks-5d95ba5505a8a88f9dcff72fe2ca57940f18688f | 139220612 | `sha256:79872d1d89eb9a4afbecd53633ce660f928147c53e6820d978b6906e38b78d4e` | 2026-12-11 | solo histórico |
| 124 | 2026-11-11 23:32 | `main` | `5d95ba5505` | mihon-release-apks-5d95ba5505a8a88f9dcff72fe2ca57940f18688f | 139220612 | `sha256:47a455234cfd3fcffac8102e676e0939d4e29c47790f846d4229c541c2027287` | 2026-12-11 | ✅ bytes verificados |
| 125 | 2026-11-12 00:17 | `feature/webgpu-clean` | `d650a1ad33` | mihon-release-apks-d650a1ad338063f25ce2fb236b8993b31721f647 | 131511519 | `sha256:38c3226ede4162097156fa0986172d8cb6b3a0381837c4b9cad1907edc12e38c` | 2026-12-12 | solo histórico |
| 125 | 2026-11-12 00:17 | `feature/webgpu-clean` | `d650a1ad33` | mihon-unminified-apks-d650a1ad338063f25ce2fb236b8993b31721f647 | 210615954 | `sha256:3036d732871d87779a888ab07021a911ef46660d2fb49ddc40bf456bed8fca1b` | 2026-12-12 | solo histórico |
| 127 | 2026-11-12 00:36 | `feature/webgpu-clean` | `0ecb90ffab` | mihon-release-apks-0ecb90ffabae6c289826acc3184244d48c727d28 | 131511619 | `sha256:ffde6d59632a5f944678599f08bef567ac06a40b2b1af446fd5c29f8a3fac934` | 2026-12-12 | solo histórico |
| 127 | 2026-11-12 00:36 | `feature/webgpu-clean` | `0ecb90ffab` | mihon-unminified-apks-0ecb90ffabae6c289826acc3184244d48c727d28 | 210613016 | `sha256:633c7caecfa53383ac4dac14bf7bb61be97d196cab48211c69080ab052247094` | 2026-12-12 | solo histórico |
| 128 | 2026-11-12 02:48 | `feature/webgpu-clean` | `d324ddbf6f` | mihon-release-apks-d324ddbf6f88287962cfa74bcc6997624395de1b | 131532474 | `sha256:9689bd802d1717319d67685a0dec78fa0df0d673277914b3a30098bbec49fee9` | 2026-12-12 | solo histórico |
| 128 | 2026-11-12 02:48 | `feature/webgpu-clean` | `d324ddbf6f` | mihon-unminified-apks-d324ddbf6f88287962cfa74bcc6997624395de1b | 210679667 | `sha256:f5321893cc27911960adf6054ce7fbe9dc31ef7bf8952f82e6bfb46e92c97d96` | 2026-12-12 | solo histórico |
| 131 | 2026-11-12 03:03 | `feature/webgpu-clean` | `f6eb34c892` | mihon-release-apks-f6eb34c892d61ddf6fe2aa0a7f54be7e21e1b8c2 | 131532948 | `sha256:e3115242ea6881e0b5a074616eb13bf6fb1362bfd046363406e54ba864b94f94` | 2026-12-12 | solo histórico |
| 131 | 2026-11-12 03:03 | `feature/webgpu-clean` | `f6eb34c892` | mihon-unminified-apks-f6eb34c892d61ddf6fe2aa0a7f54be7e21e1b8c2 | 210679663 | `sha256:6d59a0927bd0e45b7f869561df4db87b60cfd7b8e4f88c1525bc75260100678e` | 2026-12-12 | solo histórico |
| 132 | 2026-11-12 03:03 | `main` | `f6eb34c892` | mihon-release-apks-f6eb34c892d61ddf6fe2aa0a7f54be7e21e1b8c2 | 131532948 | `sha256:9e1eac29ced67d4cc51d61d2f210d547ba6d76ff5e9569c51685deaf399a5f3a` | 2026-12-12 | ✅ bytes verificados |
| 132 | 2026-11-12 03:03 | `main` | `f6eb34c892` | mihon-unminified-apks-f6eb34c892d61ddf6fe2aa0a7f54be7e21e1b8c2 | 210679663 | `sha256:0933e1775d8af15870ce84c68fabe24b3bc7c120b274c7ff10160d84241a96b4` | 2026-12-12 | solo histórico |
| 133 | 2026-11-12 03:17 | `feature/webgpu-clean` | `986aa6ee45` | mihon-release-apks-986aa6ee456632f42632ae2167f355c3238089b3 | 131534008 | `sha256:68f427888cb636cfea4a78d40b9dfa05a60292b75c6e0fca4e6246521b229022` | 2026-12-12 | solo histórico |
| 133 | 2026-11-12 03:17 | `feature/webgpu-clean` | `986aa6ee45` | mihon-unminified-apks-986aa6ee456632f42632ae2167f355c3238089b3 | 210684796 | `sha256:55f0731ff21d313f29bcc21374216dfbe5e844c96f94c9abf3a03dba82421c6c` | 2026-12-12 | solo histórico |
| 134 | 2026-11-12 03:17 | `main` | `986aa6ee45` | mihon-release-apks-986aa6ee456632f42632ae2167f355c3238089b3 | 131534008 | `sha256:5f51d71cbc3fcfc3d30f903576202dddc4964eb93fd6a02dc4ee4016920e3737` | 2026-12-12 | ✅ bytes verificados |
| 134 | 2026-11-12 03:17 | `main` | `986aa6ee45` | mihon-unminified-apks-986aa6ee456632f42632ae2167f355c3238089b3 | 210684796 | `sha256:d314899bdc4e2b1e44a324b4f95f7e2eab43013aedf0104bd4e26e24d2986fef` | 2026-12-12 | solo histórico |
| 135 | 2026-11-12 04:26 | `main` | `e0613ab593` | mihon-release-apks-e0613ab5939baf78ff1766e94a6f1aa36b83841d | 131532953 | `sha256:da0e1357a659886cf5bb952f79f02df9a7eaf238d463669b0c09d6c0de0b3779` | 2026-12-12 | ✅ bytes verificados |
| 135 | 2026-11-12 04:26 | `main` | `e0613ab593` | mihon-unminified-apks-e0613ab5939baf78ff1766e94a6f1aa36b83841d | 210682529 | `sha256:a4c6bb451c5788da2c53b9192c9b5a54b6b8e4b7e3ad2ce7a2cbfe9909c83a0e` | 2026-12-12 | solo histórico |
| 136 | 2026-11-12 04:27 | `feature/webgpu-clean` | `e0613ab593` | mihon-release-apks-e0613ab5939baf78ff1766e94a6f1aa36b83841d | 131532953 | `sha256:57586079c09e3e44e36b2820ac585f35654bfbac08c87b1c845c0c504839da16` | 2026-12-12 | solo histórico |
| 136 | 2026-11-12 04:27 | `feature/webgpu-clean` | `e0613ab593` | mihon-unminified-apks-e0613ab5939baf78ff1766e94a6f1aa36b83841d | 210682529 | `sha256:31f208dd198edc45e6974d8cae226626c0df8efc2881482636a397d66ca74f4c` | 2026-12-12 | solo histórico |
| 137 | 2026-11-12 04:59 | `main` | `519c7b4335` | mihon-release-apks-519c7b43356020e55d44b1b31ae7f4863cb74c07 | 131553523 | `sha256:e7c7d1df6c24cd1ff8ed5c12cd1aed789af03e18cb3187a963e8900f2d663a48` | 2026-12-12 | ✅ bytes verificados |
| 137 | 2026-11-12 04:59 | `main` | `519c7b4335` | mihon-unminified-apks-519c7b43356020e55d44b1b31ae7f4863cb74c07 | 210711948 | `sha256:9f567b198daccd2b61ec0ffc752d1e5dd62a19274cb5acc728458055c400fd89` | 2026-12-12 | solo histórico |
| 138 | 2026-11-12 04:59 | `feature/webgpu-clean` | `519c7b4335` | mihon-release-apks-519c7b43356020e55d44b1b31ae7f4863cb74c07 | 131553523 | `sha256:4bfb2304e4c4e2bc3173cbf81ace6b726ca17bdcb118c484a3cbe54ae4bf066b` | 2026-12-12 | solo histórico |
| 138 | 2026-11-12 04:59 | `feature/webgpu-clean` | `519c7b4335` | mihon-unminified-apks-519c7b43356020e55d44b1b31ae7f4863cb74c07 | 210711164 | `sha256:0c242288e150cd4355bf125ae48ebf3e5b46172f5468e1d6f28d2825543584fe` | 2026-12-12 | solo histórico |
| 139 | 2026-11-12 05:14 | `feature/webgpu-clean` | `4f4b79c466` | mihon-release-apks-4f4b79c466f201cf3e54b8d42823ca0f9277eeb3 | 131553754 | `sha256:e645d350a0c1f86eff1ba9d58a23e1b3535d9f5b05f48c00e53df84aee51cfa6` | 2026-12-12 | solo histórico |
| 139 | 2026-11-12 05:14 | `feature/webgpu-clean` | `4f4b79c466` | mihon-unminified-apks-4f4b79c466f201cf3e54b8d42823ca0f9277eeb3 | 210712015 | `sha256:31774f2beef46a98e96e83fed4db11e960f432428abcd5502253764c8f2a2ab4` | 2026-12-12 | solo histórico |
| 140 | 2026-11-12 05:14 | `main` | `4f4b79c466` | mihon-release-apks-4f4b79c466f201cf3e54b8d42823ca0f9277eeb3 | 131553754 | `sha256:712fb9aa83b010841949e8587767abcfc670266094e3184beaf60f16430908e1` | 2026-12-12 | ✅ bytes verificados |
| 140 | 2026-11-12 05:14 | `main` | `4f4b79c466` | mihon-unminified-apks-4f4b79c466f201cf3e54b8d42823ca0f9277eeb3 | 210712015 | `sha256:8bfcf499de6cc92ce80d17d4b2e7fe6465f28e1d0b3018877ed482149d13011a` | 2026-12-12 | solo histórico |
| 143 | 2026-11-15 02:46 | `main` | `39b1b5881f` | mihon-release-apks-39b1b5881f772d1368a2808b1f899586d071455b | 131559885 | `sha256:cfbc0250c89579ef9647ffe55690bb925bb8e65e2f75255df86d02d7e378e306` | 2026-12-15 | ✅ bytes verificados |
| 143 | 2026-11-15 02:46 | `main` | `39b1b5881f` | mihon-unminified-apks-39b1b5881f772d1368a2808b1f899586d071455b | 210718634 | `sha256:b4bf935866d46cd2ae47dfd424fc8e8d76d12ad7afb1d8d1377d5ccbe3b1c5df` | 2026-12-15 | solo histórico |
| 144 | 2026-11-15 03:22 | `main` | `110c8f76e9` | mihon-release-apks-110c8f76e94cd895abc347c20aee273f6a29aed8 | 131560748 | `sha256:b77925af60ee75b46541cf4c9b17fef72d55b9bf17ef897b41188507139993dd` | 2026-12-15 | ✅ bytes verificados |
| 144 | 2026-11-15 03:22 | `main` | `110c8f76e9` | mihon-unminified-apks-110c8f76e94cd895abc347c20aee273f6a29aed8 | 210719222 | `sha256:b2541bc704b6ffb4d7302576deb3baa2636c0cff5be933302ff60991eca07b72` | 2026-12-15 | solo histórico |
| 146 | 2026-11-15 22:57 | `docs/agents-md` | `045dc8acd4` | emperorscan-extension-apk-045dc8acd4fb5436f3400d96973cd2c0e7ff9506 | 813531 | `sha256:7d794fd391e2946087a009b42c6e8f1dae0c3c9c7e6098d26d402226ddebf137` | 2026-12-15 | solo histórico |
| 146 | 2026-11-15 22:57 | `docs/agents-md` | `045dc8acd4` | mhscans-extension-apk-045dc8acd4fb5436f3400d96973cd2c0e7ff9506 | 708589 | `sha256:0c9d8aba6d7ec144a0ac774c2457597d481cf62e996f6ec97d0555e50e032e9e` | 2026-12-15 | solo histórico |
| 146 | 2026-11-15 22:57 | `docs/agents-md` | `045dc8acd4` | mihon-release-apks-045dc8acd4fb5436f3400d96973cd2c0e7ff9506 | 131559957 | `sha256:4ef094f9ff97cffd1548909aff1c6263efb806627e33097a77037d01c408edc4` | 2026-12-15 | solo histórico |
| 146 | 2026-11-15 22:57 | `docs/agents-md` | `045dc8acd4` | mihon-unminified-apks-045dc8acd4fb5436f3400d96973cd2c0e7ff9506 | 210719821 | `sha256:dbbce249d35101625f6e75825c182ce35f06af232b5f37577e8fa0134ac0cbfa` | 2026-12-15 | solo histórico |
| 147 | 2026-11-15 23:08 | `docs/agents-md` | `33f00d0a79` | mihon-release-apks-33f00d0a799d1a1e06382dbb42ec925ca2e0a282 | 131560178 | `sha256:22af3dd90a31c2dd6789c653faa545faba51733aa03ca5836bd4dce10e0a5c5a` | 2026-12-15 | solo histórico |
| 147 | 2026-11-15 23:08 | `docs/agents-md` | `33f00d0a79` | mihon-unminified-apks-33f00d0a799d1a1e06382dbb42ec925ca2e0a282 | 210718792 | `sha256:2699a94b684b58f00451655400eb4bd2d63503504c3d4fe0728cb51fcac3479c` | 2026-12-15 | solo histórico |
| 149 | 2026-11-15 23:19 | `docs/agents-md` | `dcee288020` | mihon-release-apks-dcee288020924744cfed6089686c6c4a39183cfd | 131560794 | `sha256:608b0dab088d0e4e1e9a588a8170a7de9a9d09089d4a93147013c2a74808639d` | 2026-12-15 | solo histórico |
| 149 | 2026-11-15 23:19 | `docs/agents-md` | `dcee288020` | mihon-unminified-apks-dcee288020924744cfed6089686c6c4a39183cfd | 210719982 | `sha256:72924e46dfe4d57c06f0bdd839639e7bea956a1f6c65b59a755e5d7a1fe356b2` | 2026-12-15 | solo histórico |
| 150 | 2026-11-15 23:26 | `fix/webgpu-viewer-modes` | `4f4b79c466` | emperorscan-extension-apk-4f4b79c466f201cf3e54b8d42823ca0f9277eeb3 | 813526 | `sha256:fc464ef4106463970c09f534c2f1bfba68ae9bc4be1df787bb12351862b8c84b` | 2026-12-15 | solo histórico |
| 150 | 2026-11-15 23:26 | `fix/webgpu-viewer-modes` | `4f4b79c466` | mhscans-extension-apk-4f4b79c466f201cf3e54b8d42823ca0f9277eeb3 | 708594 | `sha256:e2525b5a7031827bb30aa3514a58f1145d9a781df2e03775f8628a92778f63e6` | 2026-12-15 | solo histórico |
| 150 | 2026-11-15 23:26 | `fix/webgpu-viewer-modes` | `4f4b79c466` | mihon-release-apks-4f4b79c466f201cf3e54b8d42823ca0f9277eeb3 | 131553754 | `sha256:cdb88daa4d2e983c298260c9f735d06081f31948b7b53be83a76a3d9930b59aa` | 2026-12-15 | solo histórico |
| 150 | 2026-11-15 23:26 | `fix/webgpu-viewer-modes` | `4f4b79c466` | mihon-unminified-apks-4f4b79c466f201cf3e54b8d42823ca0f9277eeb3 | 210712015 | `sha256:67e64b9ce452a0b09f0017ed5d025ef7e6458175117e7dde3236b56b06cb84fc` | 2026-12-15 | solo histórico |
| 156 | 2026-11-15 23:44 | `fix/webgpu-viewer-modes` | `5edb057eb0` | mihon-release-apks-5edb057eb0e3ea7390c2a2c28493dd38edbe9bd0 | 131559215 | `sha256:f2db4d623c5e88a996ea795c30b21f127d038a40e8e976320d99a4169bb4c001` | 2026-12-15 | solo histórico |
| 156 | 2026-11-15 23:44 | `fix/webgpu-viewer-modes` | `5edb057eb0` | mihon-unminified-apks-5edb057eb0e3ea7390c2a2c28493dd38edbe9bd0 | 210712052 | `sha256:c4e1f8f188e95d94074c7c99d12fad2995e264f5bc4789b5faea383ac7dbd715` | 2026-12-15 | solo histórico |
| 159 | 2026-11-16 00:04 | `fix/webgpu-viewer-modes` | `69a522c700` | mihon-release-apks-69a522c700d907124145b26d91b783c96c5e9427 | 131560140 | `sha256:9c0749db4a8d2f115aa155c2fb2beb8d6d59e865b1b4c215a10b88ccac86dd5c` | 2026-12-16 | solo histórico |
| 159 | 2026-11-16 00:04 | `fix/webgpu-viewer-modes` | `69a522c700` | mihon-unminified-apks-69a522c700d907124145b26d91b783c96c5e9427 | 210706598 | `sha256:50a3530b0cb3e117621421a1971643d002e5d84a027504950793a4ed181ea74a` | 2026-12-16 | solo histórico |
| 161 | 2026-11-16 01:51 | `fix/webgpu-migration-metadata` | `d8e6efe943` | mihon-release-apks-d8e6efe94336d74ea91a5c76ed6bd1e8b3b25143 | 131584426 | `sha256:ac6155aa5fedaa93a06b286856863bc937aebfd94b4c65b0408d8a1afa21ce13` | 2026-12-16 | solo histórico |
| 161 | 2026-11-16 01:51 | `fix/webgpu-migration-metadata` | `d8e6efe943` | mihon-unminified-apks-d8e6efe94336d74ea91a5c76ed6bd1e8b3b25143 | 210770676 | `sha256:471985b9cdfc02f076c800f748741112794b07980faab5693a04ae0b74dbc654` | 2026-12-16 | solo histórico |
| 162 | 2026-11-16 02:05 | `fix/webgpu-migration-metadata` | `b80e3352bf` | mihon-release-apks-b80e3352bf9dcfec0466610bac01db238f11f9b3 | 131584109 | `sha256:479e4e703ae0c081318c8d7bec6585a7cc9fa5866844b3ff50f6272c6b960d39` | 2026-12-16 | solo histórico |
| 162 | 2026-11-16 02:05 | `fix/webgpu-migration-metadata` | `b80e3352bf` | mihon-unminified-apks-b80e3352bf9dcfec0466610bac01db238f11f9b3 | 210767823 | `sha256:33d990e93f637e6a69743361eb4fafe8531bc8fcaa11f9ba0fd55c7d9ee74426` | 2026-12-16 | solo histórico |
| 163 | 2026-11-16 03:12 | `fix/update-without-uninstall-docs` | `039eca714b` | emperorscan-extension-apk-039eca714b273ab5e8f581c915cb6a8233237fc8 | 813533 | `sha256:eeb1a6aa467518f59983684c799c07e002908cbe89e18fb9cb2c2c561831028b` | 2026-12-16 | solo histórico |
| 163 | 2026-11-16 03:12 | `fix/update-without-uninstall-docs` | `039eca714b` | mhscans-extension-apk-039eca714b273ab5e8f581c915cb6a8233237fc8 | 708586 | `sha256:62080a79354a8de4a840d2a8273d475b4aeb479fd9b58cf9005e78fced8e64ed` | 2026-12-16 | solo histórico |
| 163 | 2026-11-16 03:12 | `fix/update-without-uninstall-docs` | `039eca714b` | mihon-release-apks-039eca714b273ab5e8f581c915cb6a8233237fc8 | 131553469 | `sha256:49ca37a55b8ca277a1ce144fa06824e6e535867a9de27d661c103bc7847c1ca8` | 2026-12-16 | solo histórico |
| 163 | 2026-11-16 03:12 | `fix/update-without-uninstall-docs` | `039eca714b` | mihon-unminified-apks-039eca714b273ab5e8f581c915cb6a8233237fc8 | 210710450 | `sha256:0582702c5b0e568ac909c7fbdbda6ab18e06f44262ced54287d99eaa70930034` | 2026-12-16 | solo histórico |
| 164 | 2026-11-16 03:30 | `fix/update-without-uninstall-docs` | `b4e7f45782` | mihon-release-apks-b4e7f45782176817cb13d9d0e94f199b937c16a1 | 131553384 | `sha256:a556f4262dd12824df27db20906d92fccaa1f4f2ebdde7dcf191c1309fd11bd7` | 2026-12-16 | solo histórico |
| 164 | 2026-11-16 03:30 | `fix/update-without-uninstall-docs` | `b4e7f45782` | mihon-unminified-apks-b4e7f45782176817cb13d9d0e94f199b937c16a1 | 210710454 | `sha256:11066eefdf51c262632c219b24d9161d59df70734239bbb22724ddbee52f7731` | 2026-12-16 | solo histórico |
| 166 | 2026-11-16 04:55 | `fix/update-without-uninstall-docs` | `13b42ad7a6` | mihon-release-apks-13b42ad7a61f0989b67a6d1f70a5f400de841e09 | 131553650 | `sha256:51505417f06c77f80122731dc71c14a0828b8de878c218f7cb816396c7cb957a` | 2026-12-16 | solo histórico |
| 166 | 2026-11-16 04:55 | `fix/update-without-uninstall-docs` | `13b42ad7a6` | mihon-unminified-apks-13b42ad7a61f0989b67a6d1f70a5f400de841e09 | 210710814 | `sha256:4bc4b819b957dc11faa8047eef16ba3e0026e8272fb1d6f58ca4371309428f16` | 2026-12-16 | solo histórico |
| 168 | 2026-11-16 15:42 | `fix/migration-guards` | `fa79d5b761` | mihon-release-apks-fa79d5b761b68a6c23e607503a9434f6e2ab588e | 131596101 | `sha256:481279f84f986edb3934bd5a9db8c58b6fd99b2b31b91497c26d6cd7165ca486` | 2026-12-16 | solo histórico |
| 168 | 2026-11-16 15:42 | `fix/migration-guards` | `fa79d5b761` | mihon-unminified-apks-fa79d5b761b68a6c23e607503a9434f6e2ab588e | 210778437 | `sha256:d1702f7112d83e6635aa536d7e3d521d3bf67fa475b7c0370a14487cc55b2846` | 2026-12-16 | solo histórico |
| 169 | 2026-11-16 15:57 | `fix/migration-guards` | `da8ba290c0` | mihon-release-apks-da8ba290c0db9803ee8c039a0ac3934e24ace870 | 131596414 | `sha256:ff240150441bf563fd0cc48de1989596506429a56f835bf4374cef75f0d5935c` | 2026-12-16 | solo histórico |
| 169 | 2026-11-16 15:57 | `fix/migration-guards` | `da8ba290c0` | mihon-unminified-apks-da8ba290c0db9803ee8c039a0ac3934e24ace870 | 210779890 | `sha256:abda707712cf323654f7753b1b0546deccf4b20bc9bd31c4320dc885cb567eb5` | 2026-12-16 | solo histórico |
| 170 | 2026-11-16 17:28 | `fix/migration-guards` | `a29e72c683` | mihon-release-apks-a29e72c6835ee0dcfb8ebfe6e42f7c17a681ebe8 | 131615372 | `sha256:64d9dbe35a025c65143558c7d8fe54e405953ae7d796ac2bed4d91b792216350` | 2026-12-16 | solo histórico |
| 170 | 2026-11-16 17:28 | `fix/migration-guards` | `a29e72c683` | mihon-unminified-apks-a29e72c6835ee0dcfb8ebfe6e42f7c17a681ebe8 | 210800544 | `sha256:966076c720d083a2335aa04f51560b620f8a343f667feab34b52af3c7b84c873` | 2026-12-16 | solo histórico |
| 171 | 2026-11-16 17:39 | `fix/migration-guards` | `d0f2f3756f` | mihon-release-apks-d0f2f3756f9b727264a3296b229518ca6f0dfa75 | 131615323 | `sha256:28ac8eb168d3d60d0402c4ebce97d4d2a8bcb6306de1dcab8cd502205bee66ad` | 2026-12-16 | solo histórico |
| 171 | 2026-11-16 17:39 | `fix/migration-guards` | `d0f2f3756f` | mihon-unminified-apks-d0f2f3756f9b727264a3296b229518ca6f0dfa75 | 210800959 | `sha256:9deeb7fcbd62f6a9ceaa2dc252c0e7e431d3f4862fca66625850ee1cfe0c81ee` | 2026-12-16 | solo histórico |
| 172 | 2026-11-16 18:47 | `fix/migration-guards` | `056ba96813` | mihon-release-apks-056ba968135f4677e34dc41fc4527401d6b66802 | 131618710 | `sha256:1b9c63c763a753a89c32c9d474c75d3f401ebb51cc2505dd4da4deed7b105d13` | 2026-12-16 | solo histórico |
| 172 | 2026-11-16 18:47 | `fix/migration-guards` | `056ba96813` | mihon-unminified-apks-056ba968135f4677e34dc41fc4527401d6b66802 | 210805438 | `sha256:fe758bb1ab47ecd90f4b150fc90257892089bf1802e6de112647c1a3a7b0ba4e` | 2026-12-16 | solo histórico |
| 173 | 2026-11-16 19:00 | `fix/migration-guards` | `c932f8b511` | mihon-release-apks-c932f8b51100658bb876c4cc1bde3928cc1f2ce4 | 131618224 | `sha256:59c539e36641919cce060baae6e6a3a164dde40c64f273c5e2dbdba329cae20e` | 2026-12-16 | solo histórico |
| 173 | 2026-11-16 19:00 | `fix/migration-guards` | `c932f8b511` | mihon-unminified-apks-c932f8b51100658bb876c4cc1bde3928cc1f2ce4 | 210805254 | `sha256:ba97d2b2219409a7e27cf8e90ea61d2b2ac487e7079cedb77a26a3a782b69dec` | 2026-12-16 | solo histórico |
| 174 | 2026-11-16 20:50 | `fix/migration-guards` | `e50f42e5a8` | mihon-release-apks-e50f42e5a872375f25eba4f1f4cc51f3a1200227 | 131618304 | `sha256:5d5d0c35d6493e39d9379672c6a34e813789e1154e58cd28fabf425e821ce25a` | 2026-12-16 | solo histórico |
| 174 | 2026-11-16 20:50 | `fix/migration-guards` | `e50f42e5a8` | mihon-unminified-apks-e50f42e5a872375f25eba4f1f4cc51f3a1200227 | 210805008 | `sha256:582ec2f2925197b836eecd8611c0c6e9ddb9731e560e13b2928fd92c3d7b7d59` | 2026-12-16 | solo histórico |
| 175 | 2026-11-17 04:38 | `fix/viewer-y-migracion` | `518b77b2af` | emperorscan-extension-apk-518b77b2af810893350beaacc525023757ac9f5f | 813536 | `sha256:3f79e4ef65df389af53c0a94d7d7223e7c7541906919702b1f26e9d54250e820` | 2026-12-17 | solo histórico |
| 175 | 2026-11-17 04:38 | `fix/viewer-y-migracion` | `518b77b2af` | mhscans-extension-apk-518b77b2af810893350beaacc525023757ac9f5f | 708592 | `sha256:ad499bc31ef72dd92c06fa69beafdd521d9159ebbf3564005a50e23dc8babaf9` | 2026-12-17 | solo histórico |
| 175 | 2026-11-17 04:38 | `fix/viewer-y-migracion` | `518b77b2af` | mihon-release-apks-518b77b2af810893350beaacc525023757ac9f5f | 131617957 | `sha256:4a3201f5d5e9c45e8eb72ae08a56204cf730afe81b73a38d3a98ddc1b34997bf` | 2026-12-17 | solo histórico |
| 175 | 2026-11-17 04:38 | `fix/viewer-y-migracion` | `518b77b2af` | mihon-unminified-apks-518b77b2af810893350beaacc525023757ac9f5f | 210819346 | `sha256:190c52d40b3cf19a933c158d8634f085c603be22ba2032f8dfad9b23a61d77d5` | 2026-12-17 | solo histórico |
| 176 | 2026-11-17 18:13 | `fix/webview-fetch-pr1` | `2222d6ca05` | mihon-release-apks-2222d6ca057494b49f183f3d9ddabe283b84737d | 131567091 | `sha256:37befa3143cd80c117e1bf646ae9f982774577a90a797226db79fb60018952eb` | 2026-12-17 | solo histórico |
| 176 | 2026-11-17 18:13 | `fix/webview-fetch-pr1` | `2222d6ca05` | mihon-unminified-apks-2222d6ca057494b49f183f3d9ddabe283b84737d | 210720824 | `sha256:244064217aa3b563f4df0cdfe317833265b5f8eba1556d04030c8ddf5917f0c6` | 2026-12-17 | solo histórico |
| 182 | 2026-11-17 18:28 | `feature/webgpu-clean` | `33b3483c34` | mihon-release-apks-33b3483c34dca026f6f1fa9868ab0f38efbcd40e | 131630171 | `sha256:c46c071bd5e7e56acb43a20c9bd0b21d21eaa93d76174b403c8e3069662f4391` | 2026-12-17 | solo histórico |
| 182 | 2026-11-17 18:28 | `feature/webgpu-clean` | `33b3483c34` | mihon-unminified-apks-33b3483c34dca026f6f1fa9868ab0f38efbcd40e | 210839421 | `sha256:0fcc2321066f10a9521ac4ff8719c0af53f3372cb0d2507ca80490596c4a876b` | 2026-12-17 | solo histórico |
| 183 | 2026-11-17 18:35 | `main` | `2e8c9f94d7` | mihon-release-apks-2e8c9f94d78aeff512623018097338b11ff6aade | 131629618 | `sha256:29150e01adf35ea4fcdffa46ad692d936e7432fd02a4a6932f8dc0de6acd9bc7` | 2026-12-17 | ✅ bytes verificados |
| 183 | 2026-11-17 18:35 | `main` | `2e8c9f94d7` | mihon-unminified-apks-2e8c9f94d78aeff512623018097338b11ff6aade | 210839912 | `sha256:9fca8c35c140abf9edbdfa27f4bd33889da95ff25ad40e7194b2a622a21fedf5` | 2026-12-17 | solo histórico |
| 184 | 2026-11-17 18:35 | `feature/webgpu-clean` | `2e8c9f94d7` | mihon-release-apks-2e8c9f94d78aeff512623018097338b11ff6aade | 131629618 | `sha256:4a76ea9d754976bc4043758c31b0c61d2f85bbb6d9c213bd230baf6f8e1e4ab2` | 2026-12-17 | solo histórico |
| 184 | 2026-11-17 18:35 | `feature/webgpu-clean` | `2e8c9f94d7` | mihon-unminified-apks-2e8c9f94d78aeff512623018097338b11ff6aade | 210839912 | `sha256:fef65b83b0e0e44813974fd0f5924530f78298253811dd885799f05bd0815b59` | 2026-12-17 | solo histórico |
| 185 | 2026-11-17 18:46 | `main` | `2563408454` | mihon-release-apks-256340845494794b3791f27e7807e04590513181 | 131630698 | `sha256:0b232dc9b35735083d2dc5d67c929b1b6301eb885e745897385c8a02153065a6` | 2026-12-17 | ✅ bytes verificados |
| 185 | 2026-11-17 18:46 | `main` | `2563408454` | mihon-unminified-apks-256340845494794b3791f27e7807e04590513181 | 210839992 | `sha256:a03aa626b4819dedd2929958f148262c1767cefaa572d9cd4c3dcbad921b1176` | 2026-12-17 | solo histórico |
| 186 | 2026-11-17 19:38 | `main` | `736fa6baa1` | mihon-release-apks-736fa6baa1771512aff0a0b64b7591b86ffcf77c | 131630533 | `sha256:bff4ee1d33fe69dc3404c6a2d9fb57492779a90c8dfbc081761449c52c52b863` | 2026-12-17 | ✅ bytes verificados |
| 186 | 2026-11-17 19:38 | `main` | `736fa6baa1` | mihon-unminified-apks-736fa6baa1771512aff0a0b64b7591b86ffcf77c | 210839157 | `sha256:f7c615e8c83c37c1cb61c6f3aba4edf61ce463f33996295cf83adc9dc0dcfc31` | 2026-12-17 | solo histórico |
| 187 | 2026-11-17 19:38 | `feature/webgpu-clean` | `736fa6baa1` | mihon-release-apks-736fa6baa1771512aff0a0b64b7591b86ffcf77c | 131630533 | `sha256:94177f0064049b66f161a5fa598857a1578640147c4043dedd28bcee2f376100` | 2026-12-17 | solo histórico |
| 187 | 2026-11-17 19:38 | `feature/webgpu-clean` | `736fa6baa1` | mihon-unminified-apks-736fa6baa1771512aff0a0b64b7591b86ffcf77c | 210839157 | `sha256:bdc3e35a07f4c868e8770f8fc7ffe34d522d24c55460a5f4140d82557f327da1` | 2026-12-17 | solo histórico |
| 188 | 2026-11-19 03:14 | `main` | `18dc37a071` | mihon-release-apks-18dc37a07140f6c43c8fb267f53a9d595dc7c493 | 131629954 | `sha256:5c574870bd95515c816f1d6f1d7a98711c96b59012cff42217b618a63de37f52` | 2026-12-19 | ✅ bytes verificados |
| 188 | 2026-11-19 03:14 | `main` | `18dc37a071` | mihon-unminified-apks-18dc37a07140f6c43c8fb267f53a9d595dc7c493 | 210839363 | `sha256:18bf04b1ade3f1202724c59f08dc8c23133aa11d32f3a09749dbb1d0f3a2cad2` | 2026-12-19 | solo histórico |
| 191 | 2026-11-24 18:49 | `fix/viewer-refactor-t1-t7` | `2df1dd9e66` | mihon-release-apks-2df1dd9e6691a106dfd7a03bc68e3b82aa5e39da | 131637069 | `sha256:fc022f98a3d166fcf2824dcaba76d6e0257f85c1542ebf5249890c5c8d4e9079` | 2026-12-24 | solo histórico |
| 191 | 2026-11-24 18:49 | `fix/viewer-refactor-t1-t7` | `2df1dd9e66` | mihon-unminified-apks-2df1dd9e6691a106dfd7a03bc68e3b82aa5e39da | 210880784 | `sha256:ae425b14beeb37e5f303456e506dea48d2a826c0afef1352b51ef8f2ee3c1567` | 2026-12-24 | solo histórico |
| 192 | 2026-11-24 18:56 | `fix/viewer-refactor-t1-t7` | `fa91a3923c` | mihon-release-apks-fa91a3923c7bccf1618d6886de81fc00f003f49d | 131637248 | `sha256:f2ae2c58b75f693afba03143309633224009a57d8a31262c91b0363272097e7d` | 2026-12-24 | solo histórico |
| 192 | 2026-11-24 18:56 | `fix/viewer-refactor-t1-t7` | `fa91a3923c` | mihon-unminified-apks-fa91a3923c7bccf1618d6886de81fc00f003f49d | 210880601 | `sha256:aece0f22e42b3ee64e2921e533da306ea2632dd08ec4c87915385e131256a03a` | 2026-12-24 | solo histórico |
| 196 | 2026-11-24 19:14 | `main` | `b5ab3bb840` | mihon-release-apks-b5ab3bb840c589ef34898a0d2b5585351b3842eb | 131643533 | `sha256:f974a547dc7e265f2b3bbaa687b8847ea99fcd7268cb3e3b058b391767642519` | 2026-12-24 | ✅ bytes verificados |
| 196 | 2026-11-24 19:14 | `main` | `b5ab3bb840` | mihon-unminified-apks-b5ab3bb840c589ef34898a0d2b5585351b3842eb | 210883845 | `sha256:3c0d55f6817a446e111f3d96d702848f7f6e2a36c0a7c22a26c51699da8ed315` | 2026-12-24 | solo histórico |
| 197 | 2026-11-24 19:21 | `main` | `697add9e46` | mihon-release-apks-697add9e46f4e222e2c9e2ed9ac247b53974d969 | 131643748 | `sha256:4af49151db03568e618efdce84b294b5103dcf23fce4724cccfc1d49d3f70cb1` | 2026-12-24 | ✅ bytes verificados |
| 197 | 2026-11-24 19:21 | `main` | `697add9e46` | mihon-unminified-apks-697add9e46f4e222e2c9e2ed9ac247b53974d969 | 210883946 | `sha256:d40c1760c1a495f940761bc45c48c0187990dc0adaf099df2e077d576fc020e8` | 2026-12-24 | solo histórico |
| 198 | 2026-11-24 19:29 | `main` | `e4dcc2e43a` | mihon-release-apks-e4dcc2e43ac5eecd9dbe3e54c18f533a67c47807 | 131643645 | `sha256:d486ff339fc206233b97b4292960dcfd474e10ce198337819cd239e00c9340a2` | 2026-12-24 | ✅ bytes verificados |
| 198 | 2026-11-24 19:29 | `main` | `e4dcc2e43a` | mihon-unminified-apks-e4dcc2e43ac5eecd9dbe3e54c18f533a67c47807 | 210883587 | `sha256:a13a2d6714248e0210e657397a3a9a1f877e8af6458d68f8f881c39d9e9503e7` | 2026-12-24 | solo histórico |
| 199 | 2026-11-24 19:41 | `main` | `aada310bb8` | mihon-release-apks-aada310bb8d9e0442b627f5f1d9190293c35ea99 | 131643920 | `sha256:2c6068128285beb4d78d08ebcb721cab816affbf99e151d63c2b3d6c50261deb` | 2026-12-24 | ✅ bytes verificados |
| 199 | 2026-11-24 19:41 | `main` | `aada310bb8` | mihon-unminified-apks-aada310bb8d9e0442b627f5f1d9190293c35ea99 | 210883537 | `sha256:bb8dff4e01f71ccf9506f0a8325670ced739aa5416e90fe9e95ea4dd462dc153` | 2026-12-24 | solo histórico |
| 200 | 2026-11-24 21:58 | `ci/branch-checks` | `4c4bda34ed` | emperorscan-extension-apk-4c4bda34edad1804ffe3bcd964d15e2e57a3b7f7 | 813531 | `sha256:920ec210c5c76a4c0d917c30ace348ebb14d4c877bdbf982c045d64c91d6e14b` | 2026-12-24 | solo histórico |
| 200 | 2026-11-24 21:58 | `ci/branch-checks` | `4c4bda34ed` | mhscans-extension-apk-4c4bda34edad1804ffe3bcd964d15e2e57a3b7f7 | 708589 | `sha256:7d7a5f52158cb5d5b6e29d377d642a2ced957e366b70435725700a3ee0f11912` | 2026-12-24 | solo histórico |
| 200 | 2026-11-24 21:58 | `ci/branch-checks` | `4c4bda34ed` | mihon-release-apks-4c4bda34edad1804ffe3bcd964d15e2e57a3b7f7 | 131643204 | `sha256:2d7af78150f595024b3ad2505b1a004af38116394c9eddc79f74cdf78fbde81f` | 2026-12-24 | solo histórico |
| 200 | 2026-11-24 21:58 | `ci/branch-checks` | `4c4bda34ed` | mihon-unminified-apks-4c4bda34edad1804ffe3bcd964d15e2e57a3b7f7 | 210883732 | `sha256:7c9a021b0c081ffcca5bb47973b73dd6af19b109d598c7057c2ff4e4272211b5` | 2026-12-24 | solo histórico |
| 202 | 2026-11-24 22:10 | `ci/branch-checks` | `a6ba615077` | mihon-release-apks-a6ba61507731420beb23454dbca06c4dd6830906 | 131644021 | `sha256:ea02db4cbe157587bde7a62159cf0afe1497c4a64a635b461522f9c045365bb8` | 2026-12-24 | solo histórico |
| 202 | 2026-11-24 22:10 | `ci/branch-checks` | `a6ba615077` | mihon-unminified-apks-a6ba61507731420beb23454dbca06c4dd6830906 | 210883515 | `sha256:e103616f8170667945107300e130cf179995420bc89fd7d7105ec340a8ecef70` | 2026-12-24 | solo histórico |
| 204 | 2026-11-24 22:10 | `main` | `4b0c11f29f` | mihon-release-apks-4b0c11f29f05ddc9688ee7a890bd18abeee68631 | 131643104 | `sha256:1068317e6a7246d6658475fddfb5cb5e480596638af216a3cd24027a9202c3a7` | 2026-12-24 | ✅ bytes verificados |
| 204 | 2026-11-24 22:10 | `main` | `4b0c11f29f` | mihon-unminified-apks-4b0c11f29f05ddc9688ee7a890bd18abeee68631 | 210883987 | `sha256:5a19bb8b3544f45e07939dc480720139f42a32b0e83036b4b0c60c7d5b09ab89` | 2026-12-24 | solo histórico |
| 206 | 2026-11-24 22:31 | `main` | `cd49eb4423` | emperorscan-extension-apk-cd49eb4423d371919e79c252188cf704b0c4a41a | 813528 | `sha256:4027d69ca50997a913f30f1e93bc12df65704ff4016b17778ed83c8b1a62ddc7` | 2026-12-24 | solo histórico |
| 206 | 2026-11-24 22:31 | `main` | `cd49eb4423` | mhscans-extension-apk-cd49eb4423d371919e79c252188cf704b0c4a41a | 708594 | `sha256:8115862ac1e9d02b02748f2b38baf6a6ca5ef469f46a24a23c6a83d0a59c0e79` | 2026-12-24 | solo histórico |
| 206 | 2026-11-24 22:31 | `main` | `cd49eb4423` | mihon-release-apks-cd49eb4423d371919e79c252188cf704b0c4a41a | 131643587 | `sha256:5f58c59fbad58aa6f4c91fc0768f101de75e794d26f31b38de324ac93cb2a1ed` | 2026-12-24 | ✅ bytes verificados |
| 206 | 2026-11-24 22:31 | `main` | `cd49eb4423` | mihon-unminified-apks-cd49eb4423d371919e79c252188cf704b0c4a41a | 210883877 | `sha256:57934c8be2ed574e18309c963b6cde24f9d5c51fb491b345fd4175efa1667aa0` | 2026-12-24 | solo histórico |
| 207 | 2026-11-24 22:43 | `main` | `7a1fcdc96d` | mihon-release-apks-7a1fcdc96dadef8925c719fced20700f1844ebe8 | 131643958 | `sha256:4740a2a472e2fc031a38bd0c24c56254426bf763305dfe636c525a0f34f99c26` | 2026-12-24 | ✅ bytes verificados |
| 207 | 2026-11-24 22:43 | `main` | `7a1fcdc96d` | mihon-unminified-apks-7a1fcdc96dadef8925c719fced20700f1844ebe8 | 210883777 | `sha256:893da3dfc2ce25836368ca98ed1a85b5153cf68d54b3871e3c4753c311b863b9` | 2026-12-24 | solo histórico |
| 214 | 2026-11-25 00:18 | `fix/migration-guard-fixes` | `b9ea139a52` | mihon-release-apks-b9ea139a52b15e0fb489f434afa89701f58c0af9 | 131644602 | `sha256:e4e6e85217fe9f84ac9d43152d5b5b4aba6c51d784b0d68ae10c19d3ed2669bd` | 2026-12-25 | solo histórico |
| 214 | 2026-11-25 00:18 | `fix/migration-guard-fixes` | `b9ea139a52` | mihon-unminified-apks-b9ea139a52b15e0fb489f434afa89701f58c0af9 | 210884144 | `sha256:b48f776cc7e0ac595059ae02d21ee107864f3a8d56e50a3db0acd40a71570939` | 2026-12-25 | solo histórico |
| 218 | 2026-11-25 00:52 | `fix/migration-guard-fixes` | `e734e1c948` | mihon-release-apks-e734e1c948f31a894123e09a88ecac18f3d088dc | 131641662 | `sha256:9296005561ece12051cf1094665ac4bdc84f2544accd42fa1f1e1bd4a404bef4` | 2026-12-25 | solo histórico |
| 218 | 2026-11-25 00:52 | `fix/migration-guard-fixes` | `e734e1c948` | mihon-unminified-apks-e734e1c948f31a894123e09a88ecac18f3d088dc | 210885682 | `sha256:da2354894acaed7eb8249a5546d0ece73149dbbb65a9472a4ce34e760a7f3b84` | 2026-12-25 | solo histórico |
| 227 | 2026-11-25 02:52 | `fix/migration-guard-fixes` | `e106b71fc2` | mihon-release-apks-e106b71fc2b9787f26ea950f1c56ab1bf9f84a82 | 131642972 | `sha256:5637400333b408432d86491479fd84a885416fea31bb6d88c11b6d399f71eb57` | 2026-12-25 | solo histórico |
| 227 | 2026-11-25 02:52 | `fix/migration-guard-fixes` | `e106b71fc2` | mihon-unminified-apks-e106b71fc2b9787f26ea950f1c56ab1bf9f84a82 | 210887205 | `sha256:0f0937b12709eb0f7d817e76343f254d69a345fc951b8e248078c7eae3050a3b` | 2026-12-25 | solo histórico |
| 229 | 2026-11-25 03:33 | `fix/migration-guard-fixes` | `8cbe04c02c` | mihon-release-apks-8cbe04c02cfff85e6417eddf9a6a1a31d49d2508 | 131645485 | `sha256:b42eeb47d4adcd03fe9e59b34affe2a90e3c2867bcedba529f577c6611eb790f` | 2026-12-25 | solo histórico |
| 229 | 2026-11-25 03:33 | `fix/migration-guard-fixes` | `8cbe04c02c` | mihon-unminified-apks-8cbe04c02cfff85e6417eddf9a6a1a31d49d2508 | 210887703 | `sha256:03cf7f61ed3b6179a027755ba64d7f2b8411e0ca48e1eb6804d62949d8eb7072` | 2026-12-25 | solo histórico |
| 230 | 2026-11-25 03:42 | `fix/migration-guard-fixes` | `e70f383168` | mihon-release-apks-e70f3831681f276f64bf4010e21be4a688e414f6 | 131645287 | `sha256:6993aa71d14eccc8234fc29e861601c80c2395fd4b974a2fa7bdfe9bd38fcb3b` | 2026-12-25 | solo histórico |
| 230 | 2026-11-25 03:42 | `fix/migration-guard-fixes` | `e70f383168` | mihon-unminified-apks-e70f3831681f276f64bf4010e21be4a688e414f6 | 210888082 | `sha256:db5772919495a1cf9c29e43599862e216566bf9bfa0c6773919a53f45d7ab15c` | 2026-12-25 | solo histórico |
| 231 | 2026-11-25 03:50 | `main` | `152027f991` | mihon-release-apks-152027f99169bb64e82b108ae536d63c30333a83 | 131644659 | `sha256:0b8ddda8f7ede2f6e0301e5e5991c58d83c6ca0f49b3009757761afeb8a14a1e` | 2026-12-25 | ✅ bytes verificados |
| 231 | 2026-11-25 03:50 | `main` | `152027f991` | mihon-unminified-apks-152027f99169bb64e82b108ae536d63c30333a83 | 210887479 | `sha256:91f642e6c71a0d12f3e227e5c8c833072d7e754b2ee223e202ea6e98ef7076c5` | 2026-12-25 | solo histórico |
| 239 | 2026-11-25 05:10 | `feat/reader-page-state-wiring` | `f520f77960` | mihon-release-apks-f520f77960f5b4006b8195c919ca741fabf6d38a | 131674747 | `sha256:b54c184879511cae67f565da44e08de14c071f64486922235ed06dd80d0f414c` | 2026-12-25 | solo histórico |
| 239 | 2026-11-25 05:10 | `feat/reader-page-state-wiring` | `f520f77960` | mihon-unminified-apks-f520f77960f5b4006b8195c919ca741fabf6d38a | 210888523 | `sha256:85bb8b35a1de5265e76c578f81e044725346c8fe0ea2914da5480c4423734da8` | 2026-12-25 | solo histórico |
| 240 | 2026-11-25 05:20 | `feat/reader-page-state-wiring` | `fd0f2d0004` | mihon-release-apks-fd0f2d00047526deb7eb23145d35c2de91ca5006 | 131675008 | `sha256:bb6e4f445cc4c3a5ccfbb6ef695e668c37bc67f17e80fb6a75f9b362c3ac9595` | 2026-12-25 | solo histórico |
| 240 | 2026-11-25 05:20 | `feat/reader-page-state-wiring` | `fd0f2d0004` | mihon-unminified-apks-fd0f2d00047526deb7eb23145d35c2de91ca5006 | 210888741 | `sha256:a0aaf28213dbef2729c8180bf6182fbe615784e7d4842ed03cdafa131d45bdf4` | 2026-12-25 | solo histórico |
| 241 | 2026-11-25 15:39 | `feat/t6-page-state-machine-wiring` | `c2de25f6e4` | emperorscan-extension-apk-c2de25f6e4649f675aaa884f267c63444844372c | 813524 | `sha256:b0e46ebf271af1734cf238cea641a6fd74cf92efc058dd8a811603d781db2485` | 2026-12-25 | solo histórico |
| 241 | 2026-11-25 15:39 | `feat/t6-page-state-machine-wiring` | `c2de25f6e4` | mhscans-extension-apk-c2de25f6e4649f675aaa884f267c63444844372c | 708591 | `sha256:a194701998eaa425d1d2a0c9ff23d3407822b8fca7b62d4f9bf3b0bd76ae78b5` | 2026-12-25 | solo histórico |
| 241 | 2026-11-25 15:39 | `feat/t6-page-state-machine-wiring` | `c2de25f6e4` | mihon-release-apks-c2de25f6e4649f675aaa884f267c63444844372c | 131674589 | `sha256:4c3b676f6508a9fe36f07a50896fc9ca43cc6798e704d760fb6a49aba087412c` | 2026-12-25 | solo histórico |
| 241 | 2026-11-25 15:39 | `feat/t6-page-state-machine-wiring` | `c2de25f6e4` | mihon-unminified-apks-c2de25f6e4649f675aaa884f267c63444844372c | 210888779 | `sha256:de01fa031b78cfbf438ee828dccc961f356262a33114a56308840d74ebb21c1b` | 2026-12-25 | solo histórico |
| 242 | 2026-11-25 15:54 | `feat/t6-page-state-machine-wiring` | `0c394887b7` | mihon-release-apks-0c394887b70e1100cf5ee8dfebb8b48ef9921656 | 131675023 | `sha256:7ac6def329e920b142ceec191b0c11e24b597babcb4a73f528ded5c28eb37761` | 2026-12-25 | solo histórico |
| 242 | 2026-11-25 15:54 | `feat/t6-page-state-machine-wiring` | `0c394887b7` | mihon-unminified-apks-0c394887b70e1100cf5ee8dfebb8b48ef9921656 | 210889656 | `sha256:1a9a3538a2362a65ee6f8cc336937620bf6682ea12eb419ac3f3233b030c7d1f` | 2026-12-25 | solo histórico |
| 246 | 2026-11-25 18:10 | `feat/t6-page-state-machine-wiring` | `083edad9d3` | mihon-release-apks-083edad9d35bffae4f7b41d2efa1ce5c86181172 | 131669760 | `sha256:6068a0e1d9e0004681cca471360d62e0a05870183d7ab46bc99712a3e0d40fbf` | 2026-12-25 | solo histórico |
| 246 | 2026-11-25 18:10 | `feat/t6-page-state-machine-wiring` | `083edad9d3` | mihon-unminified-apks-083edad9d35bffae4f7b41d2efa1ce5c86181172 | 210895020 | `sha256:b08bf9a0cb6334b37e0f3a2c7b104dff0dedbcec74b5781ce712afa14a7a856e` | 2026-12-25 | solo histórico |
| 247 | 2026-11-25 18:19 | `feat/t6-page-state-machine-wiring` | `362c432b1c` | mihon-release-apks-362c432b1cb56089324b043071d9baf7b765e3ca | 131669905 | `sha256:8f4080fd1d09d95049edc353d1ca7ae274d0982e254af7703bbefb5d8df7255f` | 2026-12-25 | solo histórico |
| 247 | 2026-11-25 18:19 | `feat/t6-page-state-machine-wiring` | `362c432b1c` | mihon-unminified-apks-362c432b1cb56089324b043071d9baf7b765e3ca | 210893747 | `sha256:accb919a8ace2c2377a6df51dfceabdec3beded8b796401e2580914225d30cf0` | 2026-12-25 | solo histórico |
| 248 | 2026-11-25 18:29 | `feat/t6-page-state-machine-wiring` | `d372263584` | mihon-release-apks-d372263584db846e91225bc8b5e4abddfdbcb57a | 131669762 | `sha256:79c29c038de7d656b837fb5c2fa0ad2303b7e3748c1a6ac0225f69cad3182a88` | 2026-12-25 | solo histórico |
| 248 | 2026-11-25 18:29 | `feat/t6-page-state-machine-wiring` | `d372263584` | mihon-unminified-apks-d372263584db846e91225bc8b5e4abddfdbcb57a | 210894166 | `sha256:00c6cc82592d073e426f5dcef7be6bdc9fb62e84c372fd81e4c3925f2427bba7` | 2026-12-25 | solo histórico |
| 251 | 2026-11-25 20:37 | `feat/t6-page-state-machine-wiring` | `e3ade9cbaa` | mihon-release-apks-e3ade9cbaa7e412d2db2ae509e845bd48e6c8aae | 131672707 | `sha256:fcba87e556414b97462f15662005b72071b4d2693252b63b8fb2d3fcf522c10f` | 2026-12-25 | solo histórico |
| 251 | 2026-11-25 20:37 | `feat/t6-page-state-machine-wiring` | `e3ade9cbaa` | mihon-unminified-apks-e3ade9cbaa7e412d2db2ae509e845bd48e6c8aae | 210893279 | `sha256:9f69770595b72d5d6233df6b0a95c255c71401900fced08d8257565a4709ddf7` | 2026-12-25 | solo histórico |
| 252 | 2026-11-25 20:45 | `feat/t6-page-state-machine-wiring` | `ce3db2d0ab` | mihon-release-apks-ce3db2d0ab7c49135328bd5d035125174d0a4060 | 131672881 | `sha256:06cf420d6787795457cb063da0647f040b31ad937259688f45d4163047aee541` | 2026-12-25 | solo histórico |
| 252 | 2026-11-25 20:45 | `feat/t6-page-state-machine-wiring` | `ce3db2d0ab` | mihon-unminified-apks-ce3db2d0ab7c49135328bd5d035125174d0a4060 | 210895956 | `sha256:ba240034d59245762f8d140711945d133f1e59fd8e94ef53487016c8474cfb9a` | 2026-12-25 | solo histórico |
| 253 | 2026-11-25 20:55 | `feat/t6-page-state-machine-wiring` | `12703d34fa` | mihon-release-apks-12703d34fa4f5f19c85c7b3e93df29f7532457cb | 131672804 | `sha256:69b26b1ddba4c97e4784537f5d1abf68941266a70c90647b90d3a4130d8262e5` | 2026-12-25 | solo histórico |
| 253 | 2026-11-25 20:55 | `feat/t6-page-state-machine-wiring` | `12703d34fa` | mihon-unminified-apks-12703d34fa4f5f19c85c7b3e93df29f7532457cb | 210893540 | `sha256:74257169b325c5eb845b3b0f178651eb1432358868d031d44cf6106bb03890a4` | 2026-12-25 | solo histórico |
| 254 | 2026-11-25 22:07 | `docs/coherencia-post-auditoria-reparador` | `509c1c6567` | emperorscan-extension-apk-509c1c65672b7519e7c1842e5e61c38e99af8454 | 813535 | `sha256:5ca4cc84d5e809b7d14f983b215024f01eefb995d16359e0dd48a7904885d8fa` | 2026-12-25 | solo histórico |
| 254 | 2026-11-25 22:07 | `docs/coherencia-post-auditoria-reparador` | `509c1c6567` | mhscans-extension-apk-509c1c65672b7519e7c1842e5e61c38e99af8454 | 708589 | `sha256:d776b83a23480820100d3bb02e970e4ce2502297b53424348e9454a4df88a1a8` | 2026-12-25 | solo histórico |
| 254 | 2026-11-25 22:07 | `docs/coherencia-post-auditoria-reparador` | `509c1c6567` | mihon-release-apks-509c1c65672b7519e7c1842e5e61c38e99af8454 | 131643993 | `sha256:21a291139458df5de18be2a0a45d512ccf62bbfa439bf765864d2fcab54b4f86` | 2026-12-25 | solo histórico |
| 254 | 2026-11-25 22:07 | `docs/coherencia-post-auditoria-reparador` | `509c1c6567` | mihon-unminified-apks-509c1c65672b7519e7c1842e5e61c38e99af8454 | 210887259 | `sha256:60cbfe6e37f3eeeca42b330bdee4c50c1714de943987e72152b174cc24d6934c` | 2026-12-25 | solo histórico |
| 256 | 2026-11-25 22:41 | `fix/reparador-enlaces-tests` | `9b8be0de2b` | mihon-release-apks-9b8be0de2b799a1ce7622c8c4c59755f54e5e702 | 131643616 | `sha256:a906815dc2bc6358951d76d41b04dab0e7ad173950d954079951579d469457af` | 2026-12-25 | solo histórico |
| 256 | 2026-11-25 22:41 | `fix/reparador-enlaces-tests` | `9b8be0de2b` | mihon-unminified-apks-9b8be0de2b799a1ce7622c8c4c59755f54e5e702 | 210887474 | `sha256:5ceee670f5fc6c04b8afd9b5420e5b4a280b2ea88cba7b87d17d405126d97b9c` | 2026-12-25 | solo histórico |
| 257 | 2026-11-25 22:54 | `fix/reparador-enlaces-tests` | `fb4701201e` | mihon-release-apks-fb4701201eaa677f17104a217e97ec6fbb955dbe | 131659177 | `sha256:623ee062295b7190c17836dbf3af88c5f23dd25a7d25c4f18b0d425d780bc75e` | 2026-12-25 | solo histórico |
| 257 | 2026-11-25 22:54 | `fix/reparador-enlaces-tests` | `fb4701201e` | mihon-unminified-apks-fb4701201eaa677f17104a217e97ec6fbb955dbe | 210899161 | `sha256:79188b86ea4f76ef15b9cb255ac1fa897e60ce570fd936b1be39c7d6c61ff556` | 2026-12-25 | solo histórico |
| 261 | 2026-11-25 23:29 | `fix/reparador-enlaces-tests` | `03fe7242c1` | mihon-release-apks-03fe7242c1f017ca28f335c41fa922608523e1c7 | 131653371 | `sha256:c66828081cb31fc5c391534035542377960e0659879d8162e8dcb845b53dada5` | 2026-12-25 | solo histórico |
| 261 | 2026-11-25 23:29 | `fix/reparador-enlaces-tests` | `03fe7242c1` | mihon-unminified-apks-03fe7242c1f017ca28f335c41fa922608523e1c7 | 210907488 | `sha256:82fb4c7ebf21b86e1557cd64795ea8c998765a35c6e173b1ddf8d6d8d4be782b` | 2026-12-25 | solo histórico |
| 262 | 2026-11-25 23:37 | `fix/reparador-enlaces-tests` | `4c5f151550` | mihon-release-apks-4c5f1515500686048e22df69486e338709cb69e1 | 131653603 | `sha256:5b2af379f7e97f0a6ba76a6cedebf58cb7826b4f6fc780351e2464b82ddf030a` | 2026-12-25 | solo histórico |
| 262 | 2026-11-25 23:37 | `fix/reparador-enlaces-tests` | `4c5f151550` | mihon-unminified-apks-4c5f1515500686048e22df69486e338709cb69e1 | 210906386 | `sha256:c747cbb5c65d84172ff0c3fd182c52e109e97a5bdc831409ed0bff7035926aba` | 2026-12-25 | solo histórico |
| 264 | 2026-11-26 00:07 | `main` | `00f465d6c0` | mihon-release-apks-00f465d6c0de747710e0aab8923c94204dd9e9ba | 131653637 | `sha256:6bdd6621adcb4c5652a1d8ba011d593fd80f56e4091fbac8467ef5029b70061f` | 2026-12-26 | ✅ bytes verificados |
| 264 | 2026-11-26 00:07 | `main` | `00f465d6c0` | mihon-unminified-apks-00f465d6c0de747710e0aab8923c94204dd9e9ba | 210908712 | `sha256:5130422bb0432b69c2574f2402164ed077a950d9f3872ce860e7bc87d226a71c` | 2026-12-26 | solo histórico |
| 265 | 2026-11-26 00:12 | `docs/post-merge-auditoria-reparador` | `0c80777a3a` | emperorscan-extension-apk-0c80777a3a4a9773afc53cbd9b22ccb0fb209c3b | 813536 | `sha256:59f03f7aaa149126fbf268b7f57997ad1b5c7c2c0198290c40cb6ea3fa94f0f5` | 2026-12-26 | solo histórico |
| 265 | 2026-11-26 00:12 | `docs/post-merge-auditoria-reparador` | `0c80777a3a` | mhscans-extension-apk-0c80777a3a4a9773afc53cbd9b22ccb0fb209c3b | 708587 | `sha256:88eb517a75a8d23d1f8fe2825d3f7ca8ac2fc08ec21abcd714cad0daf4071eaa` | 2026-12-26 | solo histórico |
| 265 | 2026-11-26 00:12 | `docs/post-merge-auditoria-reparador` | `0c80777a3a` | mihon-release-apks-0c80777a3a4a9773afc53cbd9b22ccb0fb209c3b | 131653802 | `sha256:c874bb2993bc434a2a6bdf09a587446cb93227292a8150c9a3b51c9d39c5c685` | 2026-12-26 | solo histórico |
| 265 | 2026-11-26 00:12 | `docs/post-merge-auditoria-reparador` | `0c80777a3a` | mihon-unminified-apks-0c80777a3a4a9773afc53cbd9b22ccb0fb209c3b | 210907722 | `sha256:dcdb0cd6d5fdf2bbc8c564cdca989fbdd75f51b814f59f83d06ccba808a917f2` | 2026-12-26 | solo histórico |
| 266 | 2026-11-26 02:41 | `docs/registro-fallos-c-d` | `c39eece620` | emperorscan-extension-apk-c39eece620bd326dc0ccf12d3daa261a0706d47f | 813535 | `sha256:54fd98406b646b5e60518fde7f6772641e9b48853c2bed148e5061acc6c2b361` | 2026-12-26 | solo histórico |
| 266 | 2026-11-26 02:41 | `docs/registro-fallos-c-d` | `c39eece620` | mhscans-extension-apk-c39eece620bd326dc0ccf12d3daa261a0706d47f | 708588 | `sha256:c96bd17cbd54b79180696a7a7ad51a0cf0b692e4768e5ef47ea5db25622861f0` | 2026-12-26 | solo histórico |
| 266 | 2026-11-26 02:41 | `docs/registro-fallos-c-d` | `c39eece620` | mihon-release-apks-c39eece620bd326dc0ccf12d3daa261a0706d47f | 131654215 | `sha256:aef1229e122905237052c4360b417a44fe2a964d360994f1db40ccd17000e214` | 2026-12-26 | solo histórico |
| 266 | 2026-11-26 02:41 | `docs/registro-fallos-c-d` | `c39eece620` | mihon-unminified-apks-c39eece620bd326dc0ccf12d3daa261a0706d47f | 210906750 | `sha256:ba004e3fa2d82c992ed1944f239b63041d9daa023090ce62a65b35116dd798fc` | 2026-12-26 | solo histórico |
| 267 | 2026-11-26 04:32 | `docs/post-merge-auditoria-reparador` | `03d09fdf0a` | mihon-release-apks-03d09fdf0a4229c10654a4235055922292a66fe5 | 131653554 | `sha256:c9f0bb48db6d1ba4eb68ffe749c02c06f4397f50a01dbaf12bf81581716dcf45` | 2026-12-26 | solo histórico |
| 267 | 2026-11-26 04:32 | `docs/post-merge-auditoria-reparador` | `03d09fdf0a` | mihon-unminified-apks-03d09fdf0a4229c10654a4235055922292a66fe5 | 210908208 | `sha256:3a48af8a1aa4788cb3bdea5df51cdcc15f3f228233c122fb77e6b874e08e0a22` | 2026-12-26 | solo histórico |
| 269 | 2026-11-26 04:42 | `main` | `127dfd54d9` | mihon-release-apks-127dfd54d9ae94fe0454b91b063526172621363b | 131653989 | `sha256:055027858332e664f84947d3c808dd7d9d37228684212103d34eea538d15bb65` | 2026-12-26 | ✅ bytes verificados |
| 269 | 2026-11-26 04:42 | `main` | `127dfd54d9` | mihon-unminified-apks-127dfd54d9ae94fe0454b91b063526172621363b | 210907988 | `sha256:1620e1b8ac6e8979fdd6141d64921400dbfdf0b63ef604eb304c727bdbd3492e` | 2026-12-26 | solo histórico |
| 270 | 2026-11-26 04:57 | `main` | `3d3b602c4d` | mihon-release-apks-3d3b602c4df0b3489425f08c80b3a531098c8755 | 131653756 | `sha256:b61d7962b6f0729682d858cebd6e3dcfa87ce3afbaf3cded21dd0d82f1855203` | 2026-12-26 | ✅ bytes verificados |
| 270 | 2026-11-26 04:57 | `main` | `3d3b602c4d` | mihon-unminified-apks-3d3b602c4df0b3489425f08c80b3a531098c8755 | 210908194 | `sha256:1ba85842140c1b759b9de1c594cecc7f7c5b91e50f6939aa558d68494599fa05` | 2026-12-26 | solo histórico |
| 273 | 2026-11-26 05:00 | `ci/nomenclatura-builds` | `9bba91f72f` | INSTALAR-9bba91f72f527e8db003df6204f38a40e86db55b | 131653933 | `sha256:57dbce9893aa59f5def13f9d440470d44989f689196c77c72a21a719631dfaf2` | 2026-12-26 | solo histórico |
| 273 | 2026-11-26 05:00 | `ci/nomenclatura-builds` | `9bba91f72f` | NO-INSTALAR-mihon-unminified-apks-9bba91f72f527e8db003df6204f38a40e86db55b | 210907032 | `sha256:824bbd569a98637dfb50323a0285c135dca2b787f42be033d7a479f138712c74` | 2026-12-26 | solo histórico |
| 274 | 2026-11-26 05:13 | `main` | `d017b3f2c2` | INSTALAR-d017b3f2c2ecf9de96c963d435c6ebad39213471 | 131653879 | `sha256:9e395bcd406466f9529a93edc571580cf2a5dbefe6f6e72d961cf2149a29b880` | 2026-12-26 | ✅ bytes verificados |
| 274 | 2026-11-26 05:13 | `main` | `d017b3f2c2` | NO-INSTALAR-mihon-unminified-apks-d017b3f2c2ecf9de96c963d435c6ebad39213471 | 210908515 | `sha256:a011e9ad84da5a552c62a7bc210eaabbf50f98341722dec850c01711bf872943` | 2026-12-26 | solo histórico |
| 274 | 2026-11-26 05:13 | `main` | `d017b3f2c2` | emperorscan-extension-apk-d017b3f2c2ecf9de96c963d435c6ebad39213471 | 813529 | `sha256:5e3268cc0e69916c6d277211595c66dff5ddd5483f58d7eb2528117802e1a0b2` | 2026-12-26 | solo histórico |
| 274 | 2026-11-26 05:13 | `main` | `d017b3f2c2` | mhscans-extension-apk-d017b3f2c2ecf9de96c963d435c6ebad39213471 | 708592 | `sha256:5cf83e20a504ece3859e3d5f97df3e63eb96bfdfb186a16ffe3a9ccd57e59458` | 2026-12-26 | solo histórico |
| 275 | 2026-11-26 21:37 | `feat/t6-page-state-machine-wiring` | `c6e1e129b6` | mihon-release-apks-c6e1e129b66d1bff5f42daf343dd105234af53e9 | 131672101 | `sha256:00eea8b8545fb7ee89a184ba1e271ba60ff5727d60278735ec13032243738928` | 2026-12-26 | solo histórico |
| 275 | 2026-11-26 21:37 | `feat/t6-page-state-machine-wiring` | `c6e1e129b6` | mihon-unminified-apks-c6e1e129b66d1bff5f42daf343dd105234af53e9 | 210896371 | `sha256:8ad8aab39615ee9ad27c9d3bb3014aa7b3a879275356256dd11d2606793f0b9a` | 2026-12-26 | solo histórico |
| 277 | 2026-11-26 22:28 | `feat/t6-page-state-machine-wiring` | `8926a5a28d` | mihon-release-apks-8926a5a28dd9b13544438af32dc2ccedb03097c0 | 131672464 | `sha256:cb6a10632f0f2b98b7c2d4943943f0dc563d64e2415e79f80df428a7e37a96a2` | 2026-12-26 | solo histórico |
| 277 | 2026-11-26 22:28 | `feat/t6-page-state-machine-wiring` | `8926a5a28d` | mihon-unminified-apks-8926a5a28dd9b13544438af32dc2ccedb03097c0 | 210894071 | `sha256:870ab3450e49059677a2c999851515213d894cfc4793c1ae61b3ef63428753c5` | 2026-12-26 | solo histórico |
| 278 | 2026-11-26 22:44 | `main` | `7d1092685b` | INSTALAR-7d1092685b1add6787d7dda47dd7d6348dfad310 | 131679637 | `sha256:d5bfd5577445f2c65b25d376130762ad21db55bba67bd9b33e556073cf5d1623` | 2026-12-26 | ✅ bytes verificados |
| 278 | 2026-11-26 22:44 | `main` | `7d1092685b` | NO-INSTALAR-mihon-unminified-apks-7d1092685b1add6787d7dda47dd7d6348dfad310 | 210924488 | `sha256:55d507cda536257d75c79dcefe16b39c303b62271cca202d34e2a87c62d76bd6` | 2026-12-26 | solo histórico |
| 287 | 2026-11-27 02:40 | `feat/p7-webview-fallback-all-failures` | `dcba12e2b6` | INSTALAR-dcba12e2b67205f392f070e270cf61dd564d20f1 | 131683735 | `sha256:90e4eacb791ee4b0def9177876b21c4fd2340789f6cbf241dc631b454d2a9712` | 2026-12-27 | solo histórico |
| 287 | 2026-11-27 02:40 | `feat/p7-webview-fallback-all-failures` | `dcba12e2b6` | NO-INSTALAR-mihon-unminified-apks-dcba12e2b67205f392f070e270cf61dd564d20f1 | 210956722 | `sha256:78bd025773fd5094721f22d46efddffca74636ffceabe41a36a9c230a75d1fd3` | 2026-12-27 | solo histórico |
| 288 | 2026-11-27 02:52 | `feat/p7-webview-fallback-all-failures` | `c8086ffaa6` | INSTALAR-c8086ffaa6086541ef75d1e43c42b3969660c82d | 131684001 | `sha256:21ae4573dc8b4f668d2563fa3dd75fabb5e7e8b2d780cbb75a6127cbd05eed5d` | 2026-12-27 | solo histórico |
| 288 | 2026-11-27 02:52 | `feat/p7-webview-fallback-all-failures` | `c8086ffaa6` | NO-INSTALAR-mihon-unminified-apks-c8086ffaa6086541ef75d1e43c42b3969660c82d | 210958165 | `sha256:0016cf5bbc942683f248d52b621d5d73a85c04074394302d181254096a8a8fdc` | 2026-12-27 | solo histórico |
| 289 | 2026-11-27 05:11 | `main` | `bd4aa16e67` | INSTALAR-bd4aa16e678d9d1ca8ae45f0833312fea9dde8a9 | 131683881 | `sha256:65b8ee1c972783e57eeb2e7caf3da5137e81ba514cca96be24e8b8d7a013d800` | 2026-12-27 | ✅ bytes verificados |
| 289 | 2026-11-27 05:11 | `main` | `bd4aa16e67` | NO-INSTALAR-mihon-unminified-apks-bd4aa16e678d9d1ca8ae45f0833312fea9dde8a9 | 210959196 | `sha256:70b09810c0f3e01ec92e55f2c4e4e5064e103676bd47880757c584c4fac7a364` | 2026-12-27 | solo histórico |
| 302 | 2026-11-27 05:32 | `docs/consolidacion-2026-11-27` | `df424a5ee2` | INSTALAR-df424a5ee24c833f9b7d36b7d2f867f11ae579b3 | 131683694 | `sha256:b70155e4d35cb5ede342799d9e9ea46a08f1491d274644a8ed66d67b551a6bb7` | 2026-12-27 | solo histórico |
| 302 | 2026-11-27 05:32 | `docs/consolidacion-2026-11-27` | `df424a5ee2` | NO-INSTALAR-mihon-unminified-apks-df424a5ee24c833f9b7d36b7d2f867f11ae579b3 | 210958471 | `sha256:755a835326f6ff80b57ad5f56241908322724d9625bb57835b2a68f6afd29414` | 2026-12-27 | solo histórico |
| 304 | 2026-11-27 05:32 | `fix/fallo-e-textura` | `bd4aa16e67` | INSTALAR-bd4aa16e678d9d1ca8ae45f0833312fea9dde8a9 | 131683881 | `sha256:53f44a96e3142b1c18e2ba2aea2e4e768eea17cdd90bfdb0f6c14eae93b6e485` | 2026-12-27 | solo histórico |
| 304 | 2026-11-27 05:32 | `fix/fallo-e-textura` | `bd4aa16e67` | NO-INSTALAR-mihon-unminified-apks-bd4aa16e678d9d1ca8ae45f0833312fea9dde8a9 | 210959196 | `sha256:7f522e63cf6684dba5bf84d2d74dafaa325e0f43ea5f7bf454a8024c113c96a6` | 2026-12-27 | solo histórico |
| 304 | 2026-11-27 05:32 | `fix/fallo-e-textura` | `bd4aa16e67` | emperorscan-extension-apk-bd4aa16e678d9d1ca8ae45f0833312fea9dde8a9 | 813529 | `sha256:92d01ab3aed11b4698c38cda246ed9e2581711e54ab21faeae949ddbf58a48da` | 2026-12-27 | solo histórico |
| 304 | 2026-11-27 05:32 | `fix/fallo-e-textura` | `bd4aa16e67` | mhscans-extension-apk-bd4aa16e678d9d1ca8ae45f0833312fea9dde8a9 | 708589 | `sha256:14f6c6cfa7b09855f46084d071fa214f4437bf846733031655170afa30a792af` | 2026-12-27 | solo histórico |
| 313 | 2026-11-27 06:39 | `docs/rebrand-mihonfix` | `de705a9502` | INSTALAR-de705a9502e4a32d4d412a7a9925c4c6736a29ab | 131684296 | `sha256:5501a4f0f5737974b0a56b6233955dea787646fcdc7706a7de768ad024f29cd5` | 2026-12-27 | solo histórico |
| 313 | 2026-11-27 06:39 | `docs/rebrand-mihonfix` | `de705a9502` | NO-INSTALAR-mihon-unminified-apks-de705a9502e4a32d4d412a7a9925c4c6736a29ab | 210956803 | `sha256:2e1ff86ad0bd419c2c00e4fcc54e56f9b975df610d7c860e17aed5cde5491368` | 2026-12-27 | solo histórico |
| 313 | 2026-11-27 06:39 | `docs/rebrand-mihonfix` | `de705a9502` | emperorscan-extension-apk-de705a9502e4a32d4d412a7a9925c4c6736a29ab | 813535 | `sha256:c9c2d571586a993772e2cad47fdc82c12671c44c7c9bd398d7bc3aa08d21a68e` | 2026-12-27 | solo histórico |
| 313 | 2026-11-27 06:39 | `docs/rebrand-mihonfix` | `de705a9502` | mhscans-extension-apk-de705a9502e4a32d4d412a7a9925c4c6736a29ab | 708589 | `sha256:ef0003f29a674f1f9ed6f9a365cf98893c9f949113168569b0d75e7e3b2fd46b` | 2026-12-27 | solo histórico |
| 333 | 2026-11-27 12:17 | `feat/p8-domain-override` | `2ac269feab` | INSTALAR-2ac269feabd3aaf768ad023e1e80bd6515fe7eb5 | 131740131 | `sha256:d0a7ba27d41f02c5e16ec9a84f9d86e9bceed1124b1ba9f806cd6ec4f01f410b` | 2026-12-27 | solo histórico |
| 333 | 2026-11-27 12:17 | `feat/p8-domain-override` | `2ac269feab` | NO-INSTALAR-mihon-unminified-apks-2ac269feabd3aaf768ad023e1e80bd6515fe7eb5 | 210992825 | `sha256:0ce40fb91bd977c294fb923002eabf7ab4f1dde0c9ee6711e737001902570036` | 2026-12-27 | solo histórico |
| 335 | 2026-11-27 12:18 | `docs/consolidacion-2026-11-27` | `79801c1684` | INSTALAR-79801c1684484ee240c7e92703f9bfe8311df423 | 131683833 | `sha256:615a778030d97cd7b114b4080217cbb78d733ecdb1b673d5fb0cf6613a9dce59` | 2026-12-27 | solo histórico |
| 335 | 2026-11-27 12:18 | `docs/consolidacion-2026-11-27` | `79801c1684` | NO-INSTALAR-mihon-unminified-apks-79801c1684484ee240c7e92703f9bfe8311df423 | 210957766 | `sha256:8875e50afebd4a59ba06e4cf529ddfd444bb69c9ef59abf5fd039b887b50290f` | 2026-12-27 | solo histórico |
| 338 | 2026-11-27 12:24 | `docs/rebrand-mihonfix` | `f0eb0538a2` | INSTALAR-f0eb0538a275048dd441d0a657a9627f171999ac | 131683397 | `sha256:63c740d4bacb7838082355d63cf116d892d0bcef2660ede6fa174ac1de81e8f1` | 2026-12-27 | solo histórico |
| 338 | 2026-11-27 12:24 | `docs/rebrand-mihonfix` | `f0eb0538a2` | NO-INSTALAR-mihon-unminified-apks-f0eb0538a275048dd441d0a657a9627f171999ac | 210959649 | `sha256:a3840edee7f3097e5c3ac456e426e726bb00787b35d0db4573da5bde9db913e3` | 2026-12-27 | solo histórico |
| 341 | 2026-11-27 12:42 | `feat/p5-reintentar-titulos-error` | `4b0bcb2556` | INSTALAR-4b0bcb2556480d1fe4c6b4c4ae8cddf7d9a00837 | 131689155 | `sha256:08da60265bc7eb874fb7fb8f4bccc7228265dff1239205bed8b601dc58f4030f` | 2026-12-27 | solo histórico |
| 341 | 2026-11-27 12:42 | `feat/p5-reintentar-titulos-error` | `4b0bcb2556` | NO-INSTALAR-mihon-unminified-apks-4b0bcb2556480d1fe4c6b4c4ae8cddf7d9a00837 | 210969531 | `sha256:1982e2633247d37c85c195b52d9f4ddac61f42029947a2944bf2eda7a28fea5c` | 2026-12-27 | solo histórico |
| 347 | 2026-11-27 13:24 | `fix/fallo-e-textura` | `abe9ef63ef` | INSTALAR-abe9ef63ef39f95b8129513866c2ac7be3ede4cd | 131688062 | `sha256:f936eeb806544c2a6703175bcf92f6f9130ed5b00b92f238deb35d6533f13f4e` | 2026-12-27 | solo histórico |
| 347 | 2026-11-27 13:24 | `fix/fallo-e-textura` | `abe9ef63ef` | NO-INSTALAR-mihon-unminified-apks-abe9ef63ef39f95b8129513866c2ac7be3ede4cd | 210968054 | `sha256:6d684e607085af97b081a296a263bb3b0cd81cd5dcac874689597dfedd91ea02` | 2026-12-27 | solo histórico |
| 348 | 2026-11-27 13:34 | `fix/fallo-e-textura` | `bcce14ad76` | INSTALAR-bcce14ad766b1b5b50c4028efebe528f828e6191 | 131688040 | `sha256:afec2cf5033a0c6dcfb9dabcb399ca755e55a37b43eae4246374c63a0ed8d327` | 2026-12-27 | solo histórico |
| 348 | 2026-11-27 13:34 | `fix/fallo-e-textura` | `bcce14ad76` | NO-INSTALAR-mihon-unminified-apks-bcce14ad766b1b5b50c4028efebe528f828e6191 | 210967667 | `sha256:1221fdd0f5e4924c246a249a9c523a6107e5c4fd9074955c53262dc2b6e4a5ec` | 2026-12-27 | solo histórico |
| 349 | 2026-11-27 13:56 | `main` | `8d7f056d36` | INSTALAR-8d7f056d36b1c5afffb13569067078388f77f441 | 131683317 | `sha256:2750b4f302ab3eee589f79e547108fc3da3875749ab6e4ee4e5c2ef27ecc42f2` | 2026-12-27 | ✅ bytes verificados |
| 349 | 2026-11-27 13:56 | `main` | `8d7f056d36` | NO-INSTALAR-mihon-unminified-apks-8d7f056d36b1c5afffb13569067078388f77f441 | 210957494 | `sha256:5a8dba4ba70e194d62a8ee3564b0c9969be5f0b98bda54d29345200b8a5d07f6` | 2026-12-27 | solo histórico |
| 350 | 2026-11-27 13:57 | `docs/rebrand-mihonfix` | `034c6b0e72` | INSTALAR-034c6b0e72bcd3a630ed1477657515e6a8347189 | 131683890 | `sha256:1d6901edcbd48846c89114e897b441b3c713b1bbd8bcc0dd2a19098461298259` | 2026-12-27 | solo histórico |
| 350 | 2026-11-27 13:57 | `docs/rebrand-mihonfix` | `034c6b0e72` | NO-INSTALAR-mihon-unminified-apks-034c6b0e72bcd3a630ed1477657515e6a8347189 | 210957660 | `sha256:50a0abb699685cd8294557dba7363da09bdaa55f7337c6b72163c0f04a70be72` | 2026-12-27 | solo histórico |
| 351 | 2026-11-27 14:10 | `main` | `f6638e8be8` | INSTALAR-f6638e8be87d5e67daa9959db217754a82d133a4 | 131684103 | `sha256:b96cea6d7592f83bcdcab3e0ca93dec3482bda2aef2180d7a421c3ab7b8418c0` | 2026-12-27 | ✅ bytes verificados |
| 351 | 2026-11-27 14:10 | `main` | `f6638e8be8` | NO-INSTALAR-mihon-unminified-apks-f6638e8be87d5e67daa9959db217754a82d133a4 | 210957921 | `sha256:bf636f8e41f29e3e9311bf998a76551965eccdc511cae4c9174dad6ce268a9d6` | 2026-12-27 | solo histórico |
| 352 | 2026-11-27 14:11 | `fix/fallo-e-textura` | `1b5dd0909c` | INSTALAR-1b5dd0909c07f6c4325b813ce0b33d47bfd9d9a0 | 131687905 | `sha256:5fc969398dd93083a48a4b2973ccffd0f34b45c6e1342471daa95afb16674875` | 2026-12-27 | solo histórico |
| 352 | 2026-11-27 14:11 | `fix/fallo-e-textura` | `1b5dd0909c` | NO-INSTALAR-mihon-unminified-apks-1b5dd0909c07f6c4325b813ce0b33d47bfd9d9a0 | 210968558 | `sha256:e3b0ae097056b6476b61b09ba543235e0519668d2facb9cec199049c98a7f010` | 2026-12-27 | solo histórico |
| 353 | 2026-11-27 14:19 | `main` | `5145d3cb09` | INSTALAR-5145d3cb0989aa14cea39933040a0c9ea5c23595 | 131687924 | `sha256:0a1857483744c9b1faca90a356fa4004962db2f2b6e05594963a030a5380782a` | 2026-12-27 | ✅ bytes verificados |
| 353 | 2026-11-27 14:19 | `main` | `5145d3cb09` | NO-INSTALAR-mihon-unminified-apks-5145d3cb0989aa14cea39933040a0c9ea5c23595 | 210968549 | `sha256:7633c042767de6a32adfdf042b121b2baf9b84586fdc1129ae76244d770a7a13` | 2026-12-27 | solo histórico |
| 354 | 2026-11-27 14:19 | `feat/p5-reintentar-titulos-error` | `7aa48f2897` | INSTALAR-7aa48f2897c9a914feb2148afda990bb0fd1a6d0 | 131699575 | `sha256:190bcbb97623cdb02618d25bf96b3452da98f7b8b6c629ac45af48d33797dc51` | 2026-12-27 | solo histórico |
| 354 | 2026-11-27 14:19 | `feat/p5-reintentar-titulos-error` | `7aa48f2897` | NO-INSTALAR-mihon-unminified-apks-7aa48f2897c9a914feb2148afda990bb0fd1a6d0 | 210986219 | `sha256:93a365526e3a4f7b5743c00bb8dea9b779f4691be8cb4855bdb867d583c308e5` | 2026-12-27 | solo histórico |
| 355 | 2026-11-27 14:27 | `main` | `efa411fa6d` | INSTALAR-efa411fa6d1fb488d357bc64974370dc7d9b3816 | 131700421 | `sha256:53ffbbe4e06ca6b1bcbc23575b4d6d9b33f54777e5c6d2449f8e184838de8258` | 2026-12-27 | ✅ bytes verificados |
| 355 | 2026-11-27 14:27 | `main` | `efa411fa6d` | NO-INSTALAR-mihon-unminified-apks-efa411fa6d1fb488d357bc64974370dc7d9b3816 | 210983288 | `sha256:7e1c499aff357dc261614a6781b69f77641823de6bc44778b9141bd5bc0e66c4` | 2026-12-27 | solo histórico |
| 356 | 2026-11-27 14:27 | `feat/p8-domain-override` | `7a63cb8f13` | INSTALAR-7a63cb8f13e4f8b7ed61803440666ce5b497b9c0 | 131750332 | `sha256:b7a75e70b65329dc687024c8ebc35350e52793663dfc459f5a12327b77b32204` | 2026-12-27 | solo histórico |
| 356 | 2026-11-27 14:27 | `feat/p8-domain-override` | `7a63cb8f13` | NO-INSTALAR-mihon-unminified-apks-7a63cb8f13e4f8b7ed61803440666ce5b497b9c0 | 211024295 | `sha256:0a36bfc2255a14297d1b6a772e1b4c0204b5b42b272073a3ec8aefa2da8fc8c0` | 2026-12-27 | solo histórico |
| 357 | 2026-11-27 14:40 | `main` | `2f4e0b8dcd` | INSTALAR-2f4e0b8dcd7cea6bd42e7e98e4eac84062cc7fb1 | 131750397 | `sha256:8b29dc1e727a39404fe62618e8db969bc7404fd4d8221eb0e697f589aa4ea376` | 2026-12-27 | ✅ bytes verificados |
| 357 | 2026-11-27 14:40 | `main` | `2f4e0b8dcd` | NO-INSTALAR-mihon-unminified-apks-2f4e0b8dcd7cea6bd42e7e98e4eac84062cc7fb1 | 211026635 | `sha256:4d169765eb315b094b5ee4c22d177d37ce2e2ea847072f4adbc04b9087aaf400` | 2026-12-27 | solo histórico |
| 358 | 2026-11-27 14:55 | `main` | `c196c915ef` | INSTALAR-c196c915ef7e0ed714b98a146d1a6a5449f54f44 | 131750647 | `sha256:d2883fb56735ac04d1121e53d5cb8e7f229841e064a4d7019180a603c9d151fb` | 2026-12-27 | ✅ bytes verificados |
| 358 | 2026-11-27 14:55 | `main` | `c196c915ef` | NO-INSTALAR-mihon-unminified-apks-c196c915ef7e0ed714b98a146d1a6a5449f54f44 | 211027562 | `sha256:2cba5f011d3e855f639cac9823663f2017fc84c8bf39a35f8d0e1fd8169e3f76` | 2026-12-27 | solo histórico |
| 361 | 2026-11-27 16:00 | `fix/p8-descubribilidad` | `28765134d1` | INSTALAR-28765134d1c9114643e077475c0a3886327f9187 | 131766821 | `sha256:bc4db6cd6892888fd76534ca15e0e4c295e9e2f5822e28b943c3ad94b83f0845` | 2026-12-27 | solo histórico |
| 361 | 2026-11-27 16:00 | `fix/p8-descubribilidad` | `28765134d1` | NO-INSTALAR-mihon-unminified-apks-28765134d1c9114643e077475c0a3886327f9187 | 211055196 | `sha256:b9a507540f3ff9e915bded09f1e2a4b67c5d7fef2a76e05de1f03e6b494e0344` | 2026-12-27 | solo histórico |
| 368 | 2026-11-27 16:28 | `fix/p8-descubribilidad` | `807cdec689` | INSTALAR-807cdec6890d562ee0e136d8c9a3fa70275f9d43 | 131766664 | `sha256:272adc07f7e7d821af6f0bee4ce5290ea90ddcdec83e15906bad1802c27c48a9` | 2026-12-27 | solo histórico |
| 368 | 2026-11-27 16:28 | `fix/p8-descubribilidad` | `807cdec689` | NO-INSTALAR-mihon-unminified-apks-807cdec6890d562ee0e136d8c9a3fa70275f9d43 | 211054476 | `sha256:fc5f35f284a449e2fd177252100cc3dcbc67137fa92cc246681e2c77f0a57e22` | 2026-12-27 | solo histórico |
| 370 | 2026-11-27 16:50 | `fix/fallo-f-calidad` | `833e642440` | INSTALAR-833e64244091172fc4c3a89958d19b0e376efc73 | 131753552 | `sha256:a4edb0b3d0f04bbe5713792eee420d0d6a2d57bb8dc3c4ca59c471dcd7b911e1` | 2026-12-27 | solo histórico |
| 370 | 2026-11-27 16:50 | `fix/fallo-f-calidad` | `833e642440` | NO-INSTALAR-mihon-unminified-apks-833e64244091172fc4c3a89958d19b0e376efc73 | 211035465 | `sha256:a2dfe87a65aec7a4887e033702e83a988e0976e53310bb328e6edbf1a8e09a96` | 2026-12-27 | solo histórico |
| 372 | 2026-11-27 17:06 | `fix/fallo-f-calidad` | `e0bcda4002` | INSTALAR-e0bcda40029afea0f5db0aa23a5cb740663cfacf | 131754147 | `sha256:68bd7bc59bc9c83757c11220feaebc7f78ea316376b5c875d60594f88fc9401e` | 2026-12-27 | solo histórico |
| 372 | 2026-11-27 17:06 | `fix/fallo-f-calidad` | `e0bcda4002` | NO-INSTALAR-mihon-unminified-apks-e0bcda40029afea0f5db0aa23a5cb740663cfacf | 211035118 | `sha256:00da4dbc04fa8675f6850f70e7d892e74bf2dd3838a265713642758ef96f7eb3` | 2026-12-27 | solo histórico |
| 373 | 2026-11-27 17:28 | `consolidacion/g-f` | `26c75531c2` | INSTALAR-26c75531c2890e3dfcbfde3632258cdfd9f77822 | 131767170 | `sha256:78801ffe1516cd3f0d5c309434db822db2e79a592cec234655155a6f11036c40` | 2026-12-27 | solo histórico |
| 373 | 2026-11-27 17:28 | `consolidacion/g-f` | `26c75531c2` | NO-INSTALAR-mihon-unminified-apks-26c75531c2890e3dfcbfde3632258cdfd9f77822 | 211056961 | `sha256:bc3bba73b42409c76842a41c52682ac2d2749027c9b5ca8a83bb65c92ca646e3` | 2026-12-27 | solo histórico |
| 373 | 2026-11-27 17:28 | `consolidacion/g-f` | `26c75531c2` | emperorscan-extension-apk-26c75531c2890e3dfcbfde3632258cdfd9f77822 | 813529 | `sha256:5348ee56d329f15821970309d7884653dfe656d43b26bd5b28fc8eb7479b6315` | 2026-12-27 | solo histórico |
| 373 | 2026-11-27 17:28 | `consolidacion/g-f` | `26c75531c2` | mhscans-extension-apk-26c75531c2890e3dfcbfde3632258cdfd9f77822 | 708591 | `sha256:4a12802282911b9a9296dc99c70eff66a26b3eaeb52528a89a126a398bb0ecf4` | 2026-12-27 | solo histórico |
| 374 | 2026-11-27 17:41 | `consolidacion/g-f` | `2ee20f5e8b` | INSTALAR-2ee20f5e8b731d895278d3ed448d6370c4b71c1a | 131767765 | `sha256:4a12a1e65e718186abc1ee5d9b1626c9bd1e079c2f983321f1fb55f1e7f2ee5b` | 2026-12-27 | solo histórico |
| 374 | 2026-11-27 17:41 | `consolidacion/g-f` | `2ee20f5e8b` | NO-INSTALAR-mihon-unminified-apks-2ee20f5e8b731d895278d3ed448d6370c4b71c1a | 211057279 | `sha256:86a03bd42b1bfd5b0a16cc2e56f837b372cbecd805914107de589ac658036a6e` | 2026-12-27 | solo histórico |
| 377 | 2026-11-27 19:23 | `feat/p8-fixes` | `6642eb7170` | INSTALAR-6642eb7170fc9edd42addbfd161449f6a1ac040f | 131756444 | `sha256:95e9c4e22b530e49d36c15810a31376f41fdb1ba4979c3edf0b39b5b4b0c0a7f` | 2026-12-27 | solo histórico |
| 377 | 2026-11-27 19:23 | `feat/p8-fixes` | `6642eb7170` | NO-INSTALAR-mihon-unminified-apks-6642eb7170fc9edd42addbfd161449f6a1ac040f | 211032420 | `sha256:0f99aec1fa68176aac4719932c623b932406cc8ede200e515acfa388b91f7ce0` | 2026-12-27 | solo histórico |
| 378 | 2026-11-27 19:33 | `feat/p8-fixes` | `97f66f800c` | INSTALAR-97f66f800ce53d93b61d1f2007dc9dd964ef1a67 | 131756039 | `sha256:92651c4aae383904108050a1732197eadacc93bb3d9a78c852de65c964f7b11e` | 2026-12-27 | solo histórico |
| 378 | 2026-11-27 19:33 | `feat/p8-fixes` | `97f66f800c` | NO-INSTALAR-mihon-unminified-apks-97f66f800ce53d93b61d1f2007dc9dd964ef1a67 | 211032701 | `sha256:44d49d70dcb0ab13074d7ebd76ee7ffbc98f0e931b7688aff6ce3580e0154be5` | 2026-12-27 | solo histórico |
| 390 | 2026-11-27 21:47 | `fix/p10-auditoria-p0p1` | `e8daee1939` | INSTALAR-e8daee193942ec394678353cf85c83d6d8aeb0db | 131759445 | `sha256:9b304afeec79faec74c8d0a34cb4ca428ba8c845c3cd5eb6461d65aaf82784c1` | 2026-12-27 | solo histórico |
| 390 | 2026-11-27 21:47 | `fix/p10-auditoria-p0p1` | `e8daee1939` | NO-INSTALAR-mihon-unminified-apks-e8daee193942ec394678353cf85c83d6d8aeb0db | 211057468 | `sha256:9b7a69e52f1f19439efb8a513cc6f70d79899c7365abdd10fd89b869936c2973` | 2026-12-27 | solo histórico |
| 391 | 2026-11-27 21:58 | `fix/p10-auditoria-p0p1` | `7575238521` | INSTALAR-7575238521919827a245f46c90fa8fcf1c8b7c9c | 131759359 | `sha256:b17f04379ba40952df04748f6582599c00556921b7cf216f8a049f571c76095a` | 2026-12-27 | solo histórico |
| 391 | 2026-11-27 21:58 | `fix/p10-auditoria-p0p1` | `7575238521` | NO-INSTALAR-mihon-unminified-apks-7575238521919827a245f46c90fa8fcf1c8b7c9c | 211056136 | `sha256:d032c6a372f5ed3f36a6a086e170fd22047252853cf73b26301b7cf637b79cb1` | 2026-12-27 | solo histórico |
| 392 | 2026-11-27 22:42 | `fix/p10-auditoria-p0p1` | `6faed829b2` | INSTALAR-6faed829b25871a96e6c079d23cf27e36fc0965e | 131757256 | `sha256:31a527b4abe160f9afad7e8224436be28ad4ba6186e03d554b500e97453cac5f` | 2026-12-27 | solo histórico |
| 392 | 2026-11-27 22:42 | `fix/p10-auditoria-p0p1` | `6faed829b2` | NO-INSTALAR-mihon-unminified-apks-6faed829b25871a96e6c079d23cf27e36fc0965e | 211057386 | `sha256:08d5b60a7cd0eadb201ef3f70613ca86d6be3b56edeb92153d104de1b9724e69` | 2026-12-27 | solo histórico |
| 394 | 2026-11-27 23:01 | `fix/p10-auditoria-p0p1` | `45476edf45` | INSTALAR-45476edf45a5cac586e094845deb24050c6a6b42 | 131757393 | `sha256:5f9a204a520d154c312d8f57454a0b0b798b9860885a57487e3dff5494b0f7f3` | 2026-12-27 | solo histórico |
| 394 | 2026-11-27 23:01 | `fix/p10-auditoria-p0p1` | `45476edf45` | NO-INSTALAR-mihon-unminified-apks-45476edf45a5cac586e094845deb24050c6a6b42 | 211056888 | `sha256:6e41a034d2357609b2a727ab05bdf64370d522b168e7562d14ad2deec9b4e80d` | 2026-12-27 | solo histórico |
| 396 | 2026-11-27 23:22 | `fix/p10-auditoria-p0p1` | `1367d2880d` | INSTALAR-1367d2880d0f0d0c9ec01052f0c714d52ba8e3f1 | 131761121 | `sha256:23e848a96026b14bf89311717c08e8007eda959a2f0c880292f97883141fe497` | 2026-12-27 | solo histórico |
| 396 | 2026-11-27 23:22 | `fix/p10-auditoria-p0p1` | `1367d2880d` | NO-INSTALAR-mihon-unminified-apks-1367d2880d0f0d0c9ec01052f0c714d52ba8e3f1 | 211055239 | `sha256:9ee072397bf7b0f32dbdf6f1bd51933db5a0911d6afe45c8928246bece856d05` | 2026-12-27 | solo histórico |
| 397 | 2026-11-27 23:37 | `fix/p10-auditoria-p0p1` | `9a310e5ae2` | INSTALAR-9a310e5ae2d860f20cabde5989223602a2746d85 | 131761158 | `sha256:435ae1e30a29b9e36cf5cf6056c4a62dae784341552ed35ee40d5c0d7377924d` | 2026-12-27 | solo histórico |
| 397 | 2026-11-27 23:37 | `fix/p10-auditoria-p0p1` | `9a310e5ae2` | NO-INSTALAR-mihon-unminified-apks-9a310e5ae2d860f20cabde5989223602a2746d85 | 211055823 | `sha256:d8b5d8aa25d9cb1bc94a2dcaae5a41fecf86f1b53aa36d1e17386685d35fce2c` | 2026-12-27 | solo histórico |

## 2. Cadena de custodia verificada (C1) — 57 builds

**Sellos: 57 ✅ / 0 🚨** (todos los zips descargados coinciden con su digest del
API). **Firma: 32 builds con certificado ESTABLE** (toda la línea main desde el
11/11 hasta la final `c196c91`); las 25 restantes (29/10–05/11) son de la era previa al
certificado estable (`ef0ece3`, 06/11) — coincide con lo documentado en AGENTS.md.

| run | fecha (UTC) | rama | sha | digest API | sello | zip sha256 | APK (arm64) | APK bytes | APK sha256 | firma | clases | métodos | huella DEX |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 3 | 2026-10-29 02:14 | fix-0.21.3 | `60e1288c17` | `5e7fc2b154e6a67f` | ✅ | `5e7fc2b154e6a67f` | app-arm64-v8a-release.apk | 27783284 | 2a4c8939a7c8e42f | NO estable 🚨 | 19999 | 100502 | `ba2ca6ad700ad4f5` |
| 5 | 2026-10-29 21:57 | feature/source-management | `66274d7df9` | `3357c4f44200b6cd` | ✅ | `3357c4f44200b6cd` | app-arm64-v8a-release.apk | 27813612 | d5af484fe884741b | NO estable 🚨 | 20031 | 100590 | `47791ee6db49336f` |
| 6 | 2026-10-29 22:20 | feature/source-management | `4151d80ef0` | `c51e484063939c67` | ✅ | `c51e484063939c67` | app-arm64-v8a-release.apk | 27813612 | e2d98d5c82627ca1 | NO estable 🚨 | 20036 | 100602 | `fe8e23838322d88c` |
| 7 | 2026-10-30 02:36 | feature/source-management | `61b24dc20d` | `dc7ef313c4c2efa1` | ✅ | `dc7ef313c4c2efa1` | app-arm64-v8a-release.apk | 27814084 | fd808ab7c999688f | NO estable 🚨 | 20035 | 100604 | `fcc70580ae15fbd2` |
| 9 | 2026-10-30 02:54 | fix/repair-links-main | `b1d69aeb62` | `f56e64fc5cb1d038` | ✅ | `f56e64fc5cb1d038` | app-arm64-v8a-release.apk | 38881090 | 2ae134cfdb70626c | NO estable 🚨 | 19437 | 99529 | `ef4d7d7867247c07` |
| 10 | 2026-10-30 03:12 | feature/source-management | `b481448499` | `120d698d5ecea464` | ✅ | `120d698d5ecea464` | app-arm64-v8a-release.apk | 27814084 | 7e1f65839f7ada96 | NO estable 🚨 | 20037 | 100608 | `90e098b355156438` |
| 12 | 2026-10-30 16:48 | integration | `5567a80262` | `161cd8c6e8b17c7e` | ✅ | `161cd8c6e8b17c7e` | app-arm64-v8a-release.apk | 38911390 | 5f1ab9cdebf3205a | NO estable 🚨 | 19467 | 99618 | `c13678775b2a8c44` |
| 13 | 2026-10-30 16:58 | integration | `3470c9c5e4` | `b98b34773edd1fb2` | ✅ | `b98b34773edd1fb2` | app-arm64-v8a-release.apk | 38911390 | 2086f4db6f584931 | NO estable 🚨 | 19467 | 99618 | `c13678775b2a8c44` |
| 14 | 2026-10-30 17:24 | integration | `7fb14b5911` | `abdef2039754d7ab` | ✅ | `abdef2039754d7ab` | app-arm64-v8a-release.apk | 38911858 | 5c4014d0f1a401e8 | NO estable 🚨 | 19468 | 99621 | `5132331ef7858a32` |
| 71 | 2026-11-02 19:54 | main | `af8a41dbed` | `0d2a649c2f7aeef3` | ✅ | `0d2a649c2f7aeef3` | app-arm64-v8a-release.apk | 38915618 | b94a008b02246d2a | NO estable 🚨 | 19437 | 99532 | `48f46fb4a48cae68` |
| 72 | 2026-11-02 20:05 | main | `b4a26187fc` | `bb5290b0400183b0` | ✅ | `bb5290b0400183b0` | app-arm64-v8a-release.apk | 38915618 | 977a656512ed2943 | NO estable 🚨 | 19437 | 99532 | `48f46fb4a48cae68` |
| 84 | 2026-11-03 20:52 | feature/webgpu-clean | `5f82393d49` | `61d56cc444fd3c6c` | ✅ | `61d56cc444fd3c6c` | app-arm64-v8a-release.apk | 39005762 | 0555d01884a8dac0 | NO estable 🚨 | 19621 | 100321 | `2223a73188fb135a` |
| 86 | 2026-11-03 23:08 | feature/webgpu-clean | `69769b1ec7` | `e36bec510efc2f03` | ✅ | `e36bec510efc2f03` | app-arm64-v8a-release.apk | 39007538 | 234b9a932f083002 | NO estable 🚨 | 19622 | 100327 | `164dc527f018a484` |
| 87 | 2026-11-03 23:54 | feature/webgpu-clean | `4a00792411` | `f219fb3cf249dcb3` | ✅ | `f219fb3cf249dcb3` | app-arm64-v8a-release.apk | 39007538 | c7fb754088f137e2 | NO estable 🚨 | 19622 | 100327 | `164dc527f018a484` |
| 88 | 2026-11-04 00:27 | feature/webgpu-clean | `51d158c7e7` | `e4b9c68175d827ee` | ✅ | `e4b9c68175d827ee` | app-arm64-v8a-release.apk | 39007538 | 82d307bdc089955d | NO estable 🚨 | 19622 | 100327 | `164dc527f018a484` |
| 90 | 2026-11-04 03:36 | feature/webgpu-clean | `d37ffb3de8` | `e8f0a177726743a2` | ✅ | `e8f0a177726743a2` | app-arm64-v8a-release.apk | 39009862 | 3629269eb3a018cc | NO estable 🚨 | 19629 | 100345 | `80272af39a56a401` |
| 91 | 2026-11-04 04:02 | feature/webgpu-clean | `58416b863c` | `20863d2b652fe537` | ✅ | `20863d2b652fe537` | app-arm64-v8a-release.apk | 39009862 | 1f7cf49e2c6ec8ef | NO estable 🚨 | 19629 | 100345 | `80272af39a56a401` |
| 92 | 2026-11-04 08:38 | feature/webgpu-clean | `1a39b744d9` | `6f79e7813543d1ec` | ✅ | `6f79e7813543d1ec` | app-arm64-v8a-release.apk | 39010014 | 47cfc8b5c03a2786 | NO estable 🚨 | 19629 | 100345 | `2fb5ea20c171a768` |
| 93 | 2026-11-04 08:56 | feature/webgpu-clean | `9ac6043f49` | `a4e1a7efe33f9afb` | ✅ | `a4e1a7efe33f9afb` | app-arm64-v8a-release.apk | 39010014 | e6ae7a2f50477a7f | NO estable 🚨 | 19629 | 100345 | `2fb5ea20c171a768` |
| 94 | 2026-11-04 09:50 | feature/webgpu-clean | `b681b70fe5` | `ae415833423ca764` | ✅ | `ae415833423ca764` | app-arm64-v8a-release.apk | 39010014 | c5e09122403fbd91 | NO estable 🚨 | 19629 | 100347 | `f0c4c4182d2850ed` |
| 95 | 2026-11-04 10:15 | feature/webgpu-clean | `350a7dc442` | `52f86fdd9c1132a4` | ✅ | `52f86fdd9c1132a4` | app-arm64-v8a-release.apk | 39013398 | 2e34a509a2063b37 | NO estable 🚨 | 19632 | 100357 | `5b6b0004cd2a2124` |
| 96 | 2026-11-04 10:26 | feature/webgpu-clean | `1284d6b9e3` | `c8b117ce2a09655e` | ✅ | `c8b117ce2a09655e` | app-arm64-v8a-release.apk | 39013398 | 35323b1b498339bc | NO estable 🚨 | 19632 | 100357 | `5b6b0004cd2a2124` |
| 97 | 2026-11-04 13:07 | feature/webgpu-clean | `dd8d4d830e` | `14592114794ff40a` | ✅ | `14592114794ff40a` | app-arm64-v8a-release.apk | 39013398 | baa03232c8ae5f10 | NO estable 🚨 | 19633 | 100361 | `230d43ada9781683` |
| 98 | 2026-11-04 13:24 | feature/webgpu-clean | `275bd50f02` | `de707dfeb498a3d9` | ✅ | `de707dfeb498a3d9` | app-arm64-v8a-release.apk | 39013398 | 37bf273c2afde9da | NO estable 🚨 | 19633 | 100361 | `230d43ada9781683` |
| 101 | 2026-11-05 17:31 | feature/webgpu-clean | `978c710c9f` | `3c246cb3c12d3ccd` | ✅ | `3c246cb3c12d3ccd` | app-arm64-v8a-release.apk | 39029782 | 885f1fb5634281a2 | NO estable 🚨 | 19634 | 100364 | `c3d0d693133eb780` |
| 124 | 2026-11-11 23:32 | main | `5d95ba5505` | `47a455234cfd3fcf` | ✅ | `47a455234cfd3fcf` | app-arm64-v8a-release.apk | 38918794 | 1d4134757267cb4d | ESTABLE ✅ | 19439 | 99542 | `1d4cf65241389108` |
| 132 | 2026-11-12 03:03 | main | `f6eb34c892` | `9e1eac29ced67d4c` | ✅ | `9e1eac29ced67d4c` | app-arm64-v8a-release.apk | 35186766 | 77bf7414c45aa413 | ESTABLE ✅ | 19665 | 100610 | `1f81fe3f0c101a1b` |
| 134 | 2026-11-12 03:17 | main | `986aa6ee45` | `5f51d71cbc3fcfc3` | ✅ | `5f51d71cbc3fcfc3` | app-arm64-v8a-release.apk | 35186766 | 74dd13174c0ce41b | ESTABLE ✅ | 19665 | 100610 | `1f81fe3f0c101a1b` |
| 135 | 2026-11-12 04:26 | main | `e0613ab593` | `da0e1357a659886c` | ✅ | `da0e1357a659886c` | app-arm64-v8a-release.apk | 35186766 | e8c7231fe5cd6b84 | ESTABLE ✅ | 19665 | 100610 | `1f81fe3f0c101a1b` |
| 137 | 2026-11-12 04:59 | main | `519c7b4335` | `e7c7d1df6c24cd1f` | ✅ | `e7c7d1df6c24cd1f` | app-arm64-v8a-release.apk | 35188126 | a02cbe1d4fb20fd3 | ESTABLE ✅ | 19667 | 100615 | `5eebb6a52f3a9020` |
| 140 | 2026-11-12 05:14 | main | `4f4b79c466` | `712fb9aa83b01084` | ✅ | `712fb9aa83b01084` | app-arm64-v8a-release.apk | 35188126 | fe6ce989106aca0e | ESTABLE ✅ | 19667 | 100615 | `5eebb6a52f3a9020` |
| 143 | 2026-11-15 02:46 | main | `39b1b5881f` | `cfbc0250c89579ef` | ✅ | `cfbc0250c89579ef` | app-arm64-v8a-release.apk | 35188230 | ea14bc2bc4eb04de | ESTABLE ✅ | 19667 | 100617 | `70488e362e681100` |
| 144 | 2026-11-15 03:22 | main | `110c8f76e9` | `b77925af60ee75b4` | ✅ | `b77925af60ee75b4` | app-arm64-v8a-release.apk | 35188230 | 7b910e45223db023 | ESTABLE ✅ | 19667 | 100617 | `70488e362e681100` |
| 183 | 2026-11-17 18:35 | main | `2e8c9f94d7` | `29150e01adf35ea4` | ✅ | `29150e01adf35ea4` | app-arm64-v8a-release.apk | 35208646 | 41550b088d27d4a4 | ESTABLE ✅ | 19684 | 100675 | `7da94d42f187e677` |
| 185 | 2026-11-17 18:46 | main | `2563408454` | `0b232dc9b3573508` | ✅ | `0b232dc9b3573508` | app-arm64-v8a-release.apk | 35208646 | fdb139fc604edb8c | ESTABLE ✅ | 19684 | 100675 | `7da94d42f187e677` |
| 186 | 2026-11-17 19:38 | main | `736fa6baa1` | `bff4ee1d33fe69dc` | ✅ | `bff4ee1d33fe69dc` | app-arm64-v8a-release.apk | 35208646 | d12c9c4c13ea7231 | ESTABLE ✅ | 19684 | 100675 | `7da94d42f187e677` |
| 188 | 2026-11-19 03:14 | main | `18dc37a071` | `5c574870bd95515c` | ✅ | `5c574870bd95515c` | app-arm64-v8a-release.apk | 35208646 | 2e048a8e84cd4f90 | ESTABLE ✅ | 19684 | 100675 | `7da94d42f187e677` |
| 196 | 2026-11-24 19:14 | main | `b5ab3bb840` | `f974a547dc7e265f` | ✅ | `f974a547dc7e265f` | app-arm64-v8a-release.apk | 35225030 | 77bf6dd3e99de6b5 | ESTABLE ✅ | 19686 | 100682 | `ee3a346b23cbcc40` |
| 197 | 2026-11-24 19:21 | main | `697add9e46` | `4af49151db03568e` | ✅ | `4af49151db03568e` | app-arm64-v8a-release.apk | 35225030 | 20931d9510bcc242 | ESTABLE ✅ | 19686 | 100682 | `ee3a346b23cbcc40` |
| 198 | 2026-11-24 19:29 | main | `e4dcc2e43a` | `d486ff339fc20623` | ✅ | `d486ff339fc20623` | app-arm64-v8a-release.apk | 35225030 | 243ca3ef722db973 | ESTABLE ✅ | 19686 | 100682 | `ee3a346b23cbcc40` |
| 199 | 2026-11-24 19:41 | main | `aada310bb8` | `2c6068128285beb4` | ✅ | `2c6068128285beb4` | app-arm64-v8a-release.apk | 35225030 | 5be1f29b5f433aad | ESTABLE ✅ | 19686 | 100682 | `ee3a346b23cbcc40` |
| 204 | 2026-11-24 22:10 | main | `4b0c11f29f` | `1068317e6a7246d6` | ✅ | `1068317e6a7246d6` | app-arm64-v8a-release.apk | 35225030 | 4ac9705311bcc9cc | ESTABLE ✅ | 19686 | 100682 | `ee3a346b23cbcc40` |
| 206 | 2026-11-24 22:31 | main | `cd49eb4423` | `5f58c59fbad58aa6` | ✅ | `5f58c59fbad58aa6` | app-arm64-v8a-release.apk | 35225030 | 1c8c7f714b3a9655 | ESTABLE ✅ | 19686 | 100682 | `ee3a346b23cbcc40` |
| 207 | 2026-11-24 22:43 | main | `7a1fcdc96d` | `4740a2a472e2fc03` | ✅ | `4740a2a472e2fc03` | app-arm64-v8a-release.apk | 35225030 | 930e1e20e3667ccc | ESTABLE ✅ | 19686 | 100682 | `ee3a346b23cbcc40` |
| 231 | 2026-11-25 03:50 | main | `152027f991` | `0b8ddda8f7ede2f6` | ✅ | `0b8ddda8f7ede2f6` | app-arm64-v8a-release.apk | 35225030 | 8af4436d64bbc442 | ESTABLE ✅ | 19687 | 100689 | `401fe0b7bae88178` |
| 264 | 2026-11-26 00:07 | main | `00f465d6c0` | `6bdd6621adcb4c56` | ✅ | `6bdd6621adcb4c56` | app-arm64-v8a-release.apk | 35225030 | 836829129477d9ce | ESTABLE ✅ | 19693 | 100705 | `6d4f06da1de9a7d0` |
| 269 | 2026-11-26 04:42 | main | `127dfd54d9` | `055027858332e664` | ✅ | `055027858332e664` | app-arm64-v8a-release.apk | 35225030 | 15c6a5eeba347d1e | ESTABLE ✅ | 19693 | 100705 | `6d4f06da1de9a7d0` |
| 270 | 2026-11-26 04:57 | main | `3d3b602c4d` | `b61d7962b6f07296` | ✅ | `b61d7962b6f07296` | app-arm64-v8a-release.apk | 35225030 | d0bf22e3990190a1 | ESTABLE ✅ | 19693 | 100705 | `6d4f06da1de9a7d0` |
| 274 | 2026-11-26 05:13 | main | `d017b3f2c2` | `9e395bcd406466f9` | ✅ | `9e395bcd406466f9` | MihonFix-0.21.3-build-d017b3f-20261126-0520-arm64.apk | 35225030 | c423e42c87f2de72 | ESTABLE ✅ | 19693 | 100705 | `6d4f06da1de9a7d0` |
| 278 | 2026-11-26 22:44 | main | `7d1092685b` | `d5bfd5577445f2c6` | ✅ | `d5bfd5577445f2c6` | MihonFix-0.21.3-build-7d10926-20261126-2248-arm64.apk | 35225030 | 1e6c20aaa950a4f0 | ESTABLE ✅ | 19701 | 100727 | `06b2474a4fe24f8d` |
| 289 | 2026-11-27 05:11 | main | `bd4aa16e67` | `65b8ee1c972783e5` | ✅ | `65b8ee1c972783e5` | MihonFix-0.21.3-build-bd4aa16-20261127-0516-arm64.apk | 35225030 | 63c6ce90c15568f9 | ESTABLE ✅ | 19706 | 100749 | `d257a5f141303c25` |
| 349 | 2026-11-27 13:56 | main | `8d7f056d36` | `2750b4f302ab3eee` | ✅ | `2750b4f302ab3eee` | MihonFix-0.21.3-build-8d7f056-20261127-1402-arm64.apk | 35225030 | 53d0fea00208c02f | ESTABLE ✅ | 19706 | 100749 | `d257a5f141303c25` |
| 351 | 2026-11-27 14:10 | main | `f6638e8be8` | `b96cea6d7592f83b` | ✅ | `b96cea6d7592f83b` | MihonFix-0.21.3-build-f6638e8-20261127-1416-arm64.apk | 35225022 | 71eac4416123875f | ESTABLE ✅ | 19706 | 100749 | `d257a5f141303c25` |
| 353 | 2026-11-27 14:19 | main | `5145d3cb09` | `0a1857483744c9b1` | ✅ | `0a1857483744c9b1` | MihonFix-0.21.3-build-5145d3c-20261127-1426-arm64.apk | 35225022 | 31f29ce22a9865f9 | ESTABLE ✅ | 19709 | 100757 | `c464339a3c64c9dd` |
| 355 | 2026-11-27 14:27 | main | `efa411fa6d` | `53ffbbe4e06ca6b1` | ✅ | `53ffbbe4e06ca6b1` | MihonFix-0.21.3-build-efa411f-20261127-1435-arm64.apk | 35226062 | 251c4b6d52f931dd | ESTABLE ✅ | 19717 | 100770 | `f5d396dc8a56edb3` |
| 357 | 2026-11-27 14:40 | main | `2f4e0b8dcd` | `8b29dc1e727a3940` | ✅ | `8b29dc1e727a3940` | MihonFix-0.21.3-build-2f4e0b8-20261127-1448-arm64.apk | 35245426 | eaf9aeb26af19eaa | ESTABLE ✅ | 19728 | 100800 | `4cfb9fbffcbe9cce` |
| 358 | 2026-11-27 14:55 | main | `c196c915ef` | `d2883fb56735ac04` | ✅ | `d2883fb56735ac04` | MihonFix-0.21.3-build-c196c91-20261127-1500-arm64.apk | 35245426 | 061d040d5c5eafa0 | ESTABLE ✅ | 19728 | 100800 | `4cfb9fbffcbe9cce` |

Huella DEX de la línea main: `48f46fb4…` (02/11, 19 437 clases / 99 532 métodos) →
`4cfb9fbf…` (27/11 final, 19 728 clases / 100 800 métodos). Monotonía: cada merge de
código sube o mantiene; **los merges que solo tocan docs dejan la huella DEX literalmente
idéntica** (71→72; 264→269→270→274 PRs #11/#13; 349→351 T-D1; 357→358 REGISTRO).

## 3. Diff cronológico de la línea main (C2)

Cada transición es build N → N+1 de `main`. «Definitivos» = no reaparecen en ninguna
build posterior de main.

| transición (línea main) | clases fuera | métodos fuera | reaparecen | definitivos |
|---|---|---|---|---|
| 71 2026-11-02 af8a41dbed → 72 2026-11-02 b4a26187fc | 0 | 0 | 0 | 0 |
| 72 2026-11-02 b4a26187fc → 124 2026-11-11 5d95ba5505 | 1 | 11 | 3 | 8 |
| 124 2026-11-11 5d95ba5505 → 132 2026-11-12 f6eb34c892 | 90 | 1243 | 17 | 1226 |
| 132 2026-11-12 f6eb34c892 → 134 2026-11-12 986aa6ee45 | 0 | 0 | 0 | 0 |
| 134 2026-11-12 986aa6ee45 → 135 2026-11-12 e0613ab593 | 0 | 0 | 0 | 0 |
| 135 2026-11-12 e0613ab593 → 137 2026-11-12 519c7b4335 | 0 | 7 | 0 | 7 |
| 137 2026-11-12 519c7b4335 → 140 2026-11-12 4f4b79c466 | 0 | 0 | 0 | 0 |
| 140 2026-11-12 4f4b79c466 → 143 2026-11-15 39b1b5881f | 1 | 6 | 0 | 6 |
| 143 2026-11-15 39b1b5881f → 144 2026-11-15 110c8f76e9 | 0 | 0 | 0 | 0 |
| 144 2026-11-15 110c8f76e9 → 183 2026-11-17 2e8c9f94d7 | 4 | 21 | 0 | 21 |
| 183 2026-11-17 2e8c9f94d7 → 185 2026-11-17 2563408454 | 0 | 0 | 0 | 0 |
| 185 2026-11-17 2563408454 → 186 2026-11-17 736fa6baa1 | 0 | 0 | 0 | 0 |
| 186 2026-11-17 736fa6baa1 → 188 2026-11-19 18dc37a071 | 0 | 0 | 0 | 0 |
| 188 2026-11-19 18dc37a071 → 196 2026-11-24 b5ab3bb840 | 1 | 96 | 2 | 94 |
| 196 2026-11-24 b5ab3bb840 → 197 2026-11-24 697add9e46 | 0 | 0 | 0 | 0 |
| 197 2026-11-24 697add9e46 → 198 2026-11-24 e4dcc2e43a | 0 | 0 | 0 | 0 |
| 198 2026-11-24 e4dcc2e43a → 199 2026-11-24 aada310bb8 | 0 | 0 | 0 | 0 |
| 199 2026-11-24 aada310bb8 → 204 2026-11-24 4b0c11f29f | 0 | 0 | 0 | 0 |
| 204 2026-11-24 4b0c11f29f → 206 2026-11-24 cd49eb4423 | 0 | 0 | 0 | 0 |
| 206 2026-11-24 cd49eb4423 → 207 2026-11-24 7a1fcdc96d | 0 | 0 | 0 | 0 |
| 207 2026-11-24 7a1fcdc96d → 231 2026-11-25 152027f991 | 1 | 14 | 0 | 14 |
| 231 2026-11-25 152027f991 → 264 2026-11-26 00f465d6c0 | 1 | 3 | 0 | 3 |
| 264 2026-11-26 00f465d6c0 → 269 2026-11-26 127dfd54d9 | 0 | 0 | 0 | 0 |
| 269 2026-11-26 127dfd54d9 → 270 2026-11-26 3d3b602c4d | 0 | 0 | 0 | 0 |
| 270 2026-11-26 3d3b602c4d → 274 2026-11-26 d017b3f2c2 | 0 | 0 | 0 | 0 |
| 274 2026-11-26 d017b3f2c2 → 278 2026-11-26 7d1092685b | 0 | 0 | 0 | 0 |
| 278 2026-11-26 7d1092685b → 289 2026-11-27 bd4aa16e67 | 2 | 8 | 0 | 8 |
| 289 2026-11-27 bd4aa16e67 → 349 2026-11-27 8d7f056d36 | 0 | 0 | 0 | 0 |
| 349 2026-11-27 8d7f056d36 → 351 2026-11-27 f6638e8be8 | 0 | 0 | 0 | 0 |
| 351 2026-11-27 f6638e8be8 → 353 2026-11-27 5145d3cb09 | 0 | 3 | 0 | 3 |
| 353 2026-11-27 5145d3cb09 → 355 2026-11-27 efa411fa6d | 10 | 397 | 0 | 397 |
| 355 2026-11-27 efa411fa6d → 357 2026-11-27 2f4e0b8dcd | 13 | 69 | 0 | 69 |
| 357 2026-11-27 2f4e0b8dcd → 358 2026-11-27 c196c915ef | 0 | 0 | 0 | 0 |

**Análisis de cada transición con bajas (verificado contra las huellas y el código):**

- **124→132 (11→12/11, reconciliación de la «sesión paralela», −90 clases/−1226 métodos):**
  todas las clases de bajas son locales-de-función generadas por el compilador
  (`$runMigrations$result$1`, `ca/mpreg/…$popScope$2`, lambdas sintéticas) o
  **clases-sumidero de R8** que cambian de nombre entre builds (`Ltachiyomi/data/QueryExtensionKt;`
  era un sumidero: mezclaba `subscribeToList` con `LoadingScreen`, que no tienen relación).
  Los métodos reales siguen presentes bajo otros anfitriones.
- **144→183 (15→17/11, PR #1):** baja `WebViewFetchFallbackInterceptor$FetchSession` (6 métodos)
  y reaparece como clase de primer nivel `network/interceptor/FetchSession;` (7 métodos) —
  refactor documentado del PR #1, con un método más, sin pérdida.
- **188→196 (19→24/11, −96):** el sumidero R8 `data/saver/Image;` (49 métodos ajenos entre sí:
  `CrashScreen`, `GlobalSearchItem`…) se traslada a otro sumidero; los métodos siguen.
- **353→355 y 355→357 (27/11, merges T-E/T-B/T-P8, −397/−69):** suma de (a) sumideros R8
  (`TrackKt;` → sus métodos pasan a `Llogcat/ThrowablesKt;`/`Lcom/hippo/unifile/Utils;`),
  (b) **inline de R8**: `DohProvidersKt;->doh*` deja de existir como método suelto en 357/358,
  pero la build final contiene las 11 URLs DoH (`cloudflare-dns.com`, `dns.google`, …)
  y las 9 clases `Lokhttp3/dnsoverhttps/*` — funcionalidad intacta, y (c) cambios de firma
  (`SourceOptionsDialog`, que T-P8 reescribió con la acción «Editar dominio»).
- **278→289, 135→137, 140→143, 207→231, 231→264:** bajas son solo lambdas sintéticas,
  cambios de firma de constructores (`ScoredManga(…DZZV)` → `ScoredManga(…DZZL…)` por el
  nuevo parámetro) o clases de compilador de refactorizaciones documentadas.

**Conclusión C2: cero pérdidas de código reales verificadas en la línea main.** Ninguna
clase o método de la app desapareció sin explicación (refactor con relevo, cambio de
firma, sumidero/inlining de R8).

## 4. Episodios calientes (C3)

### 29/10 — primeras builds (runs 3–14)

Las 9 primeras builds verificadas (rama `fix-0.21.3`, `feature/source-management`,
`integration`) con sello ✅. Todas sin certificado estable (era pre-`ef0ece3`):
históricamente correcto, no anomalía. Digest retenido en §1 aunque los zips de runs 3 y 5
caducan el 28/11 — a la fecha del inventario seguían vivos y quedan verificados.

### 04/11 — cherry-picks en `feature/webgpu-clean` (14 builds 03–05/11)

| transición (rama feature/webgpu-clean, episodio 03–05/11) | clases fuera | métodos fuera | reaparecen |
|---|---|---|---|
| 84 2026-11-03 5f82393d49 → 86 2026-11-03 69769b1ec7 | 0 | 9 | 0 |
| 86 2026-11-03 69769b1ec7 → 87 2026-11-03 4a00792411 | 0 | 0 | 0 |
| 87 2026-11-03 4a00792411 → 88 2026-11-04 51d158c7e7 | 0 | 0 | 0 |
| 88 2026-11-04 51d158c7e7 → 90 2026-11-04 d37ffb3de8 | 0 | 5 | 0 |
| 90 2026-11-04 d37ffb3de8 → 91 2026-11-04 58416b863c | 0 | 0 | 0 |
| 91 2026-11-04 58416b863c → 92 2026-11-04 1a39b744d9 | 0 | 5 | 0 |
| 92 2026-11-04 1a39b744d9 → 93 2026-11-04 9ac6043f49 | 0 | 0 | 0 |
| 93 2026-11-04 9ac6043f49 → 94 2026-11-04 b681b70fe5 | 0 | 0 | 0 |
| 94 2026-11-04 b681b70fe5 → 95 2026-11-04 350a7dc442 | 6 | 33 | 0 |
| 95 2026-11-04 350a7dc442 → 96 2026-11-04 1284d6b9e3 | 0 | 0 | 0 |
| 96 2026-11-04 1284d6b9e3 → 97 2026-11-04 dd8d4d830e | 0 | 0 | 0 |
| 97 2026-11-04 dd8d4d830e → 98 2026-11-04 275bd50f02 | 0 | 0 | 0 |
| 98 2026-11-04 275bd50f02 → 101 2026-11-05 978c710c9f | 4 | 17 | 0 |

Las bajas son cambios de firma (`ScoredManga`, `MigrationListItem`), clases de compilador
del frame pacing (`frameClock$1`, retirado por el perf del 13:07) y del refactor del
filtro de fuentes (`SourcesFilterContent/Screen` el 04/11 a las 10:15 — **reaparece** en la
línea final como `SourcesFilterScreenKt` con 65 líneas, refactor del gestor de fuentes).
Los métodos de `LibraryUpdateWorker` (`writeErrorFile`, `updateChapterList`) que bajan
la madrugada del 04/11 **reaparecen** en la cadena. Ningún fix quedó sin reaplicar de forma
verificable en la ventana cubierta.

### 17/11 — consolidación v0.21.3-fork.1 (el tag que nunca caduca)

- Runs 183/185/186/188 (main) con huella **idéntica entre sí** (`7da94d42…`): la
  consolidación 17–19/11 no cambió el DEX respecto de `736fa6baa` (punta del tag).
- Asset permanente del tag: `MihonFix-0.21.3-fork.1-arm64.apk`, 35 208 646 bytes,
  sha256 `d12c9c4c…`, firma ESTABLE.
- APK del run 188 (`18dc37a`, build del tag): 35 208 646 bytes, sha256 `2e048a8e…`,
  firma ESTABLE. **Mismo tamaño exacto, misma huella DEX (`7da94d42…`), distinto sha256**:
  la diferencia es de empaquetado/firma (bloque de firma v2 y metadatos del zip), no de
  contenido. Cadena: el código que el usuario recibió con el tag es el mismo que produjo
  el run 188, y ambos llevan el certificado estable.

### 26/11 — conflictos de docs en PRs #11/#13

Runs 264→269→270→274 con **huella DEX idéntica** (`6d4f06da…`): los PRs de docs
resolvieron conflictos de `REGISTRO` sin tocar el DEX. El primer merge con código (278,
`7d109268`, PR #13) sube la huella; sus bajas posteriores (278→289) son solo lambdas
sintéticas (0 métodos reales fuera).

### 27/11 — unificación de 5 PRs (build 2f4e0b8dc)

Runs 349/351 (docs, huella idéntica `d257a5f1…`), 353 (T-E), 355 (T-B), 357 (T-P8,
`2f4e0b8dc`), 358 (`c196c91`, REGISTRO, huella idéntica a 357). Las bajas de 353→355→357
están explicadas en §3 (sumideros R8 + inline de `doh*` + cambio de firma de
`SourceOptionsDialog` por T-P8). La APK de `2f4e0b8dc` verificada aquí:
sha256 APK `eaf9aeb2…` — coincide con el sha256 anotado en el REGISTRO del 27/11
(`eaf9aeb26af19eaa…`) para ese mismo artefacto: **triple coincidencia REGISTRO ↔ digest
API ↔ bytes descargados.**

## 5. Conclusiones (C4)

- **Cero pérdidas de código verificadas** en toda la cadena cubierta (57 builds, incluida
  la línea main completa 02/11→27/11). Cada baja del diff tiene explicación verificada:
  refactor con relevo (FetchSession), cambio de firma, o artefactos del minimificador R8
  (sumideros, inlining) — con contraste positivo (URLs DoH y librería `dnsoverhttps`
  presentes en el APK final).
- **Sellos: 57/57 ✅.** Ningún zip difiere de su digest oficial. Tamaños APK coherentes
  (105 MB universal-ABIs inicial → 35,2 MB arm64 desde 06/11 → 35,3 MB final).
- **Firma estable intacta** en el 100% de las builds de main desde el 11/11 (32/32).
- **Bytes perdidos por expiración: ninguno** a la fecha (518 vivos; los 6 más viejos
  expiran el 28/11 mismo).
- Etiquetado honesto: **«bytes verificados»** = 57 artefactos descargados, sellados contra
  digest, APK extraído, sha256 + firma + huella calculados (§2). **«solo histórico»** =
  461 artefactos con digest y tamaño del API anotados en §1, sin descarga en este encargo
  (prioridad main + episodios calientes). **«digest verificado»** = los 57 sellos ✅ de §2.
- Pendiente fuera de alcance: el tramo 05–10/11 de `feature/webgpu-clean` (solo extremos
  observados) y las builds de rama no cubiertas (columna «solo histórico» de §1).

## En criollo

**Nada se perdió entre build y build.** Bajamos los zips de las 57 builds clave (las 9
primeras, que caducan ya; TODA la línea main; y las del lío del 4 de noviembre) y los 57
coinciden bit a bit con el digest oficial de GitHub: nadie tocó los zips de CI. A cada
APK arm64 le sacamos el sha256, la firma y la huella de sus clases y funciones, y
comparamos build contra build. Las «desapariciones» del diff son todas cosméticas del
compresor (R8): fusiona clases parecidas y les cambia el nombre, o inlinea funciones
cortitas (v.g. los DoH: el método suelto ya no sale, pero las 11 URLs y toda la librería
DNS-over-HTTPS siguen dentro del APK final — lo comprobamos string por string). Los
cambios reales tienen relevo documentado (FetchSession pasó de clase interna a archivo
propio en el PR #1 y ganó un método). Los merges que solo tocan docs ni mueven un byte del
código (huella idéntica). El APK del tag v0.21.3-fork.1 y el del run 188 traen exactamente
el mismo código (misma huella, mismo tamaño) aunque el sha256 del archivo difiera: cambia
el empaquetado de la firma, no el contenido. Y de momento no se ha perdido ningún
artefacto por caducidad: al inventario de hoy seguían vivos los 518.

## 📲 BUILD PARA INSTALAR

**Ninguna — encargo sin build nueva (forensics).** Este encargo no toca código ni produce
APK. La build instalable sigue siendo la última de `main` (`c196c91`, artefacto
`INSTALAR-c196c915ef…`, REGISTRO 2026-11-27).
