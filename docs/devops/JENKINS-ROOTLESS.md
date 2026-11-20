# Jenkins trên Docker rootless (máy 2)

Jenkins cần Docker để build image, chạy test trong container và dựng staging tạm. Ai điều khiển được Docker của root là
chiếm được cả máy (chạy container `--privileged`, mount `/`). Vì vậy Jenkins và mọi container của pipeline chạy trên một
Docker riêng của user thường `ci` (Docker rootless): root trong container chỉ là `ci` trên máy, không có sudo, không thuộc
nhóm `docker`, không mở được socket Docker của root (`/var/run/docker.sock`).

Các dịch vụ khác của máy 2 (giám sát, SeaweedFS, k3s) vẫn chạy như cũ.

## Đường dẫn thật

| Thứ | Ở đâu (máy 2) |
|---|---|
| Docker rootless của ci | dịch vụ systemd của user: `systemctl --user status docker` (chạy dưới user ci) |
| Socket | `/run/user/1002/docker.sock` (trong container Jenkins: `/var/run/docker.sock`) |
| Dữ liệu Docker của ci (image, volume) | `/home/ci/.local/share/docker/` |
| Dữ liệu Jenkins | volume `jenkins_jenkins_home` = `/home/ci/.local/share/docker/volumes/jenkins_jenkins_home/_data` |
| Cấu hình Jenkins | `/opt/jenkins/` (bản trong Git: `deploy/jenkins/`), secret ở `/opt/jenkins/secrets/` (700, chủ ci) |
| Staging tạm thời | `/home/ci/rms-staging` |
| Bản sao lưu để staging nạp | `/home/coder/rms-backups` (ACL cho ci chỉ đọc) |

## Cài đặt (đã làm ngày 20/11/2026, user coder có sudo)

```bash
# Gói cần cho rootless: newuidmap/newgidmap, ACL, mạng cho container
sudo apt-get install -y uidmap acl slirp4netns
# User riêng, không sudo, không nhóm docker. useradd tự cấp dải uid phụ trong /etc/subuid, /etc/subgid
sudo useradd -m -s /bin/bash -c "Jenkins rootless Docker" ci

# Ubuntu 24.04 chặn user namespace của user thường (kernel.apparmor_restrict_unprivileged_userns=1): cho riêng rootlesskit
sudo tee /etc/apparmor.d/usr.bin.rootlesskit <<'EOF'
abi <abi/4.0>,
include <tunables/global>

/usr/bin/rootlesskit flags=(unconfined) {
  userns,

  include if exists <local/usr.bin.rootlesskit>
}
EOF
sudo apparmor_parser -r /etc/apparmor.d/usr.bin.rootlesskit

# Cho systemd của user quản lý cgroup CPU/RAM: pipeline giới hạn container bằng --cpus, --memory
sudo mkdir -p /etc/systemd/system/user@.service.d
printf '[Service]\nDelegate=cpu cpuset io memory pids\n' | sudo tee /etc/systemd/system/user@.service.d/delegate.conf
sudo systemctl daemon-reload
# Docker của ci chạy cả khi ci không đăng nhập, tự bật khi máy khởi động
sudo loginctl enable-linger ci

# Cài Docker rootless cho ci (docker-ce-rootless-extras đã có sẵn cùng docker-ce)
sudo -iu ci sh -c 'export XDG_RUNTIME_DIR=/run/user/1002; dockerd-rootless-setuptool.sh install'
# ~ci/.bashrc thêm: export XDG_RUNTIME_DIR=/run/user/1002 ; export DOCKER_HOST=unix:///run/user/1002/docker.sock
# ~ci/.config/docker/daemon.json: {"log-driver": "json-file", "log-opts": {"max-size": "10m", "max-file": "3"}}

# Bản sao lưu: ci chỉ đi qua /home/coder, chỉ đọc được rms-backups. backup-db.sh (máy 1) chmod 640 bản chép sang
# để ACL có hiệu lực.
sudo setfacl -m u:ci:x /home/coder
sudo setfacl -m u:ci:rx -m d:u:ci:r /home/coder/rms-backups
sudo install -d -o ci -g ci /home/ci/rms-staging

# Chuyển Jenkins: dừng bản cũ (không xóa), chép dữ liệu sang Docker của ci, chạy bản mới
cd /opt/jenkins && docker compose stop
sudo cp deploy/jenkins/{docker-compose.yml,docker-compose.override.yml,Dockerfile.jenkins} /opt/jenkins/
sudo chown -R ci:ci /opt/jenkins/secrets
docker run --rm -v jenkins_jenkins_home:/from:ro alpine:3.20 tar -C /from -cf - . \
  | sudo -iu ci sh -c 'docker volume create jenkins_jenkins_home && docker run --rm -i -v jenkins_jenkins_home:/to alpine:3.20 sh -c "tar -C /to -xf - && chown -R 0:0 /to"'
sudo -iu ci sh -c 'cd /opt/jenkins && docker compose up -d --build'
```

## Kiểm tra

```bash
sudo -iu ci docker info --format '{{.SecurityOptions}}'          # có name=rootless
sudo -iu ci docker run --rm --cpus=1 --memory=256m alpine:3.20 cat /sys/fs/cgroup/memory.max   # 268435456
# Socket Docker của root phải bị từ chối:
sudo -iu ci docker run --rm -v /var/run/docker.sock:/s docker:29-cli docker -H unix:///s ps   # permission denied
curl -s -o /dev/null -w '%{http_code}\n' https://jenkins.atlasplatform12.tech/login              # 200
```

## Dọn Jenkins cũ

Sau khi pipeline chạy đủ trên bản rootless (main #70, 20/11/2026), Jenkins cũ chạy bằng Docker root đã bị xóa: container
`jenkins`, volume `jenkins_jenkins_home` (`/var/lib/docker/volumes/jenkins_jenkins_home/_data`, khoảng 1,4 GB), image
`jenkins-with-docker:local`, network `jenkins_default` của Docker root. Dữ liệu Jenkins hiện chỉ còn ở bản rootless.
