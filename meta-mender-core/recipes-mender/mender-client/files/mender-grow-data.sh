#!/bin/sh

set -e

# All sizes are in blocks of 512 bytes

MENDER_STORAGE_DEVICE=@MENDER_STORAGE_DEVICE@
mender_storage_device_base=$(basename ${MENDER_STORAGE_DEVICE})

# 32 MB, overstimate here and should be close enough
PARTITION_OVERHEAD=65536

total_disk_size=$(cat /sys/block/${mender_storage_device_base}/size)
calculated_size=0

for part in $(find /sys/block/${mender_storage_device_base}/ -name ${mender_storage_device_base}* -exec basename {} \;); do
    if [[ "${part}" == "${mender_storage_device_base}" ]]; then
        continue
    elif [[ "${part}" == *"boot"* ]]; then # e.g mmcblk0boot0
        continue
    fi
    calculated_size=$(( $calculated_size + $(cat /sys/block/${mender_storage_device_base}/${part}/size) ))
done

calculated_size_with_overhead=$(( ${calculated_size} + ${PARTITION_OVERHEAD} ))

if [ ${calculated_size_with_overhead} -gt ${total_disk_size} ]; then
    echo "Disk has already been resized."
    exit 0
fi

# Parted will refuse to resize the parition because it needs to re-write the
# partition table and will refuse to do so unless there is a backup. This
# ensures that GPT backup headers are written to the end of the disk.
echo "w" | fdisk ${MENDER_STORAGE_DEVICE}

/usr/sbin/parted -s ${MENDER_STORAGE_DEVICE} resizepart @MENDER_DATA_PART_NUMBER@ 100%
