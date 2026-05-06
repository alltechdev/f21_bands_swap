#!/system/bin/sh
# F21 Pro pre-v3 one-shot band swap, runnable from Termux on the device itself.
# No host PC, no adb, no recovery, no fastboot.
#
# Usage:
#   sh termux_swap.sh {us|stock}
#
# One-liner from a fresh Termux:
#   pkg install -y curl tar xz-utils
#   curl -fsSL https://raw.githubusercontent.com/alltechdev/f21_bands_swap/feat/termux-one-liner/tools/termux_swap.sh | sh -s {us|stock}
#
# Requires:
#   - F21 Pro pre-v3 (NOT v3 -- red charging port, serial prefix F21PMQC25,
#     different modem, this script will brick it).
#   - Rooted via Magisk; root granted to whichever user runs this (Termux
#     user, typically u0_a<NN>).
#   - Termux with curl, tar, and xz available.
#   - ~280 MiB free in $HOME (or /data/local/tmp if HOME is unset).
#
# After running, the device sysrq-reboots in ~6 seconds. Do not interrupt.
# After boot, verify the swap with:
#     service call iphonesubinfo 1
# (the IMEI parcel reflects the new region's blob IMEI).
#
# The procedure itself is documented in docs/live_band_swap_solution.md;
# this script just downloads the four blobs and the helpers from this
# repo and invokes tools/swap.sh on the device.

set -e

REGION="${1:-}"
case "$REGION" in
    us|stock) ;;
    *) echo "usage: $0 {us|stock}" >&2; exit 1 ;;
esac

# Override these env vars to pull from a different fork or branch.
REPO_RAW="${F21_BANDS_RAW:-https://raw.githubusercontent.com/alltechdev/f21_bands_swap/feat/termux-one-liner}"

# Pick a writable work dir. Termux's $HOME is the safest default; fall back
# to /data/local/tmp (which on Magisk-rooted devices is usually writable
# by the shell user too).
WORK="${HOME:-/data/local/tmp}/.f21bands"
mkdir -p "$WORK" || {
    echo "ERROR: cannot create work dir $WORK" >&2
    exit 1
}

# Sanity: make sure we have what we need.
for tool in curl tar xz su; do
    command -v "$tool" >/dev/null 2>&1 || {
        echo "ERROR: $tool not in PATH. From Termux: pkg install -y curl tar xz-utils" >&2
        exit 1
    }
done

echo "[1/5] Downloading $REGION bands (~21 MiB compressed)..."
curl -fsSL "$REPO_RAW/bands/$REGION.tar.xz" -o "$WORK/bands.tar.xz"

echo "[2/5] Extracting partition images (~270 MiB uncompressed)..."
tar -xJf "$WORK/bands.tar.xz" -C "$WORK/"
rm -f "$WORK/bands.tar.xz"

# Verify all four expected files came out of the tarball.
for f in md1img_a.bin nvcfg.bin nvdata.bin nvram.bin; do
    [ -f "$WORK/$f" ] || {
        echo "ERROR: blob $f missing after extract; archive may be corrupt." >&2
        exit 1
    }
done

echo "[3/5] Downloading helper binary and swap script..."
curl -fsSL "$REPO_RAW/tools/mmc_probe" -o "$WORK/mmc_probe"
curl -fsSL "$REPO_RAW/tools/swap.sh"   -o "$WORK/swap.sh"
chmod +x "$WORK/mmc_probe" "$WORK/swap.sh" 2>/dev/null || true

echo "[4/5] Staging blobs to /sdcard and tools to /data/local/tmp (requires root)..."
# Build a small stage script and run it once via su, so we only get one
# Magisk grant prompt (for the staging) before the actual swap.
cat > "$WORK/.stage.sh" << EOS
#!/system/bin/sh
set -e
cp "$WORK/md1img_a.bin" /sdcard/md1img_a.bin
cp "$WORK/nvcfg.bin"    /sdcard/nvcfg.bin
cp "$WORK/nvdata.bin"   /sdcard/nvdata.bin
cp "$WORK/nvram.bin"    /sdcard/nvram.bin
cp "$WORK/mmc_probe"    /data/local/tmp/mmc_probe
cp "$WORK/swap.sh"      /data/local/tmp/swap.sh
chmod 755 /data/local/tmp/mmc_probe /data/local/tmp/swap.sh
EOS
chmod +x "$WORK/.stage.sh"
su -c "sh '$WORK/.stage.sh'"

echo "[5/5] Running swap. Device will sysrq-reboot in ~6 seconds."
echo "      After boot, verify with:  service call iphonesubinfo 1"
echo
su -mm -c 'sh /data/local/tmp/swap.sh'
