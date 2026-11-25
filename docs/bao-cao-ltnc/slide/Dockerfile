FROM debian:trixie-slim
RUN apt-get update -qq && apt-get install -y -qq --no-install-recommends python3 python3-pip python3-lxml python3-pil python3-defusedxml libreoffice-impress-nogui fonts-liberation fonts-dejavu fonts-crosextra-carlito fonts-crosextra-caladea poppler-utils >/dev/null && pip3 install -q --break-system-packages python-pptx==1.0.2 && rm -rf /var/lib/apt/lists/*
