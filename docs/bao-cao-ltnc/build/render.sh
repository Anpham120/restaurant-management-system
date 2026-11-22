#!/bin/bash
# render.sh <docx> : PDF + text từng trang trong $S/render
S=$(cd "$(dirname "$0")" && pwd)
rm -rf $S/render && mkdir -p $S/render && cp "$1" $S/render/bc.docx
docker run --rm -v $S/render:/w -w /w debian:trixie-slim sh -c 'apt-get update -qq >/dev/null && apt-get install -y -qq libreoffice-writer-nogui fonts-liberation fonts-dejavu poppler-utils python3-pil >/dev/null 2>&1 && soffice --headless --convert-to pdf bc.docx >/dev/null 2>&1 && n=$(pdfinfo bc.pdf | sed -n "s/^Pages: *//p") && for i in $(seq 1 $n); do pdftotext -layout -f $i -l $i bc.pdf t-$i.txt; done && pdftoppm -r 50 -png bc.pdf p && chown -R 1000:1000 /w'
