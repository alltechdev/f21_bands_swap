# Tiq M5 -- partition layout and band bundles

Companion to [the live band-swap procedure](live_band_swap_solution.md).
M5 uses the same backup and flashing engine as F21.

## Partition layout

Read with `mtkclient printgpt` on 2026-10-14. The connected M5 was on slot
`_a`. The four slot-A band partitions match the F21 offsets and capacities.

| Partition | Offset (bytes) | Size (MiB) | dd seek (4K) | dd count (4K) |
|-----------|---------------:|-----------:|-------------:|--------------:|
| md1img_a | 293601280 | 100 | 71680 | 25600 |
| nvcfg | 23625728 | 32 | 5768 | 8192 |
| nvdata | 57180160 | 64 | 13960 | 16384 |
| nvram | 214958080 | 64 | 52480 | 16384 |
| md1img_b | 570425344 | 100 | 139264 | 25600 |

## Band bundles

[USA](../bands/m5/us.tar.xz) was prepared from `tiqstuffs2.zip`.
The 41,252,016-byte `md1img-verified.img` was renamed `md1img_a.bin` and
zero-padded to the 104,857,600-byte partition size. The NV images are unchanged.

[EU](../bands/m5/stock.tar.xz) contains all four supplied full-partition
images unchanged.

SHA-256 of `md1img_a.bin`:

| Bundle | SHA-256 |
|--------|---------|
| USA | `c4cee657969f9233e54777192f33297a45c6d456ad7663f277315f9b8cc3a971` |
| EU | `ea33734b18490bca7026755c22e05e3d1ce209f9137ae060c3ff3e9436230a52` |

## Validation

On 2026-10-15, the user reported successful live flashing with the app in
both directions: EU → USA and USA → EU. This covers the tested M5 and setup.
