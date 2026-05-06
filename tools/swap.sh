#!/system/bin/sh
# F21 Pro live band swap (on-device, no recovery, no fastboot, no host PC).
#
# Prerequisites (host side):
#   adb push <region>/md1img_a.bin /sdcard/
#   adb push <region>/nvcfg.bin    /sdcard/
#   adb push <region>/nvdata.bin   /sdcard/
#   adb push <region>/nvram.bin    /sdcard/
#   adb push tools/mmc_probe       /data/local/tmp/mmc_probe
#   adb push tools/swap.sh         /data/local/tmp/swap.sh
#   adb shell chmod +x /data/local/tmp/mmc_probe /data/local/tmp/swap.sh
#
# Run:  adb shell su -mm -c 'sh /data/local/tmp/swap.sh'
#
# The `-mm` (mount-master) flag is required: it puts the spawned `su` in the
# SELinux context that can open /dev/block/mmcblk0 for the MMC ioctls. Plain
# `su` lands in magisk:s0 which can't.
#
# Sequencing notes (load-bearing):
# - CMD0 GO_IDLE_STATE issued first to refresh the eMMC's per-power-cycle
#   CMD29 honor quota. Without this, the eMMC silently no-ops CMD29 after
#   ~2 swaps in the same power session. The kernel's MMC error-recovery
#   path re-inits the card after our CMD0 ETIMEDOUT, restoring fresh
#   session state.
# - SWITCH (CMD6) followed by CMD29 with no intervening operation hits a
#   silent-no-op race: the eMMC's USR_WP write hasn't settled by the time
#   CMD29 is issued, so CMD29 returns clean R1 but doesn't actually clear.
#   Inserting any read command (CMD8 SEND_EXT_CSD here) between them gives
#   the eMMC time to settle the SWITCH. The script then verifies WP actually
#   cleared via CMD31 before any dd, aborting cleanly if not.
# - sysrq-b reboot (skipping ext4 shutdown sync) is required so the mounted
#   ext4 driver doesn't write its stale pagecache back over our nvcfg/nvdata
#   block-level writes during shutdown.

set -e

PROBE=/data/local/tmp/mmc_probe
DEV=/dev/block/mmcblk0

# F21 Pro pre-v3 partition offsets (4K-aligned). seek/count are in 4K blocks.
# md1img_a   sector 573440  size 100 MiB  ->  seek 71680  count 25600
# nvcfg      sector  46144  size  32 MiB  ->  seek  5768  count  8192
# nvdata     sector 111680  size  64 MiB  ->  seek 13960  count 16384
# nvram      sector 419840  size  64 MiB  ->  seek 52480  count 16384

if [ ! -x "$PROBE" ]; then
    echo "ERROR: $PROBE not found or not executable." >&2
    exit 1
fi
for f in /sdcard/md1img_a.bin /sdcard/nvcfg.bin /sdcard/nvdata.bin /sdcard/nvram.bin; do
    [ -f "$f" ] || { echo "ERROR: $f missing." >&2; exit 1; }
done
[ "$(id -u)" = "0" ] || { echo "ERROR: must run as root (use 'su -mm -c sh ...')." >&2; exit 1; }

echo "[1/7] CMD0 GO_IDLE_STATE -- forces kernel-level eMMC re-init,"
echo "      which refreshes the per-power-cycle CMD29 honor quota."
"$PROBE" "$DEV" reinit

echo "[2/7] SWITCH USR_WP US_PWR_WP_EN=1"
"$PROBE" "$DEV" switch 1 171 0x10

echo "[3/7] Settle delay (CMD8 read primes the eMMC after SWITCH so CMD29 honors)"
"$PROBE" "$DEV" ext_csd > /dev/null

echo "[4/7] CMD29 CLR_WRITE_PROT (cascades across all power-on WP groups)"
"$PROBE" "$DEV" clear_wp 419840

echo "[5/7] Verify WP actually cleared (retry up to 5x; CMD31 is occasionally flaky right after CMD29)"
WP_LINE=""
i=0
while [ $i -lt 5 ]; do
    WP_LINE=$("$PROBE" "$DEV" read_wp 419840 2>/dev/null | grep '^WP @')
    if [ -n "$WP_LINE" ]; then break; fi
    sleep 1
    i=$((i + 1))
done
echo "      $WP_LINE"
case "$WP_LINE" in
    *"00 00 00 00 00 00 00 00"*)
        echo "      WP cleared. Proceeding."
        ;;
    "")
        echo "      WARNING: read_wp ioctl kept failing; proceeding on faith of CMD29's clean R1."
        echo "      If the dd silently drops, the post-reboot device will need fastboot recovery."
        ;;
    *)
        echo "      ERROR: CMD29 silent no-op detected -- WP did not actually clear." >&2
        echo "      The eMMC's per-power-cycle CMD29 credit appears exhausted." >&2
        echo "      Recovery: power the device fully off (long-press power -> Power off)," >&2
        echo "      wait 30+ seconds, power back on, then re-run this script." >&2
        echo "      (No dd was issued; partition state is unchanged.)" >&2
        exit 1
        ;;
esac

echo "[6/7] dd all 4 partitions through /dev/block/mmcblk0"
dd if=/sdcard/md1img_a.bin of=$DEV bs=4096 seek=71680 count=25600 conv=notrunc
dd if=/sdcard/nvcfg.bin    of=$DEV bs=4096 seek=5768  count=8192  conv=notrunc
dd if=/sdcard/nvdata.bin   of=$DEV bs=4096 seek=13960 count=16384 conv=notrunc
dd if=/sdcard/nvram.bin    of=$DEV bs=4096 seek=52480 count=16384 conv=notrunc
sync

echo "[7/7] sysrq-b reboot (skips ext4 shutdown sync)"
echo 1 > /proc/sys/kernel/sysrq
sleep 1
echo b > /proc/sysrq-trigger
