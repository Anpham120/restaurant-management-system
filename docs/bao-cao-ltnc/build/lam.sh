#!/bin/bash
# lam.sh : dựng docx, render, cập nhật số trang, dựng lại
set -e; cd "$(dirname "$0")"
TPL=mau/mau-bia.docx   # bìa, header, footer, style lấy từ báo cáo triển khai
OUT=../BaoCao_LapTrinhNangCao_AtlasRestaurant.docx
for i in 1 2; do python3 build_ltnc.py $TPL $OUT; ./render.sh $OUT; python3 trang.py $PWD; done
python3 build_ltnc.py $TPL $OUT
