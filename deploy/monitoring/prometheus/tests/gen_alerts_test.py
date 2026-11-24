"""Sinh alerts.test.yml: test cho từng luật trong ../alerts.yml bằng promtool (số liệu giả, không đụng hệ thống thật).
Mỗi luật có 1 ca BẬT (số liệu vi phạm, chờ hết "for") và một số ca KHÔNG BẬT (trạng thái bình thường).
Nội dung tin (annotations) lấy từ alerts.yml rồi thay {{ $labels.x }} bằng nhãn mong đợi, nên sửa câu chữ trong
alerts.yml thì chạy lại script này. Luật mới chưa có ca test thì script dừng báo lỗi.

Chạy: python3 deploy/monitoring/prometheus/tests/gen_alerts_test.py
Kiểm: bash scripts/demo/test-canh-bao.sh
"""
import os, re, sys
import yaml

HERE = os.path.dirname(os.path.abspath(__file__))
RULES = os.path.join(HERE, '..', 'alerts.yml')
OUT = os.path.join(HERE, 'alerts.test.yml')

RMS = 'https://rms.atlasplatform12.tech/'
# alert -> (input_series, eval_time, nhãn của series kết quả (không gồm alertname, severity), ghi chú)
BAT = {
    'Watchdog': ([], '1m', {}, 'luôn bật'),
    'AppDown': ([('up{job="rms-app",instance="10.10.0.1:9181"}', '0x10')], '3m', {}, 'app không trả số liệu 1 phút'),
    'HighErrorRate': ([('http_server_requests_seconds_count{status="500",uri="/api/orders"}', '0+10x20'),
                       ('http_server_requests_seconds_count{status="200",uri="/api/orders"}', '0+10x20')], '12m', {}, '50% request lỗi 5xx'),
    'JvmHeapHigh': ([('jvm_memory_used_bytes{area="heap",id="G1 Old Gen",instance="pod-a"}', '95x20'),
                     ('jvm_memory_max_bytes{area="heap",id="G1 Old Gen",instance="pod-a"}', '100x20'),
                     ('jvm_memory_used_bytes{area="heap",id="G1 Eden Space",instance="pod-a"}', '0x20'),
                     ('jvm_memory_max_bytes{area="heap",id="G1 Eden Space",instance="pod-a"}', '-1x20')], '12m', {'instance': 'pod-a'}, 'heap 95%, bỏ vùng max = -1'),
    'DatabaseUnreachable': ([('hikaricp_connections_timeout_total{job="rms-app",instance="10.10.0.1:9181"}', '0 0 1 2 3 4 5 6')], '4m', {}, 'Hikari hết thời gian chờ kết nối'),
    'SiteDown': ([('probe_success{job="site",instance="%s"}' % RMS, '0x10')], '4m', {'job': 'site', 'instance': RMS}, 'domain thật lỗi 2 phút'),
    'CertExpiringSoon': ([('probe_ssl_earliest_cert_expiry{job="site",instance="%s"}' % RMS, '864000x200')], '2h', {'instance': RMS}, 'chứng chỉ còn khoảng 9 ngày'),
    'ProbeExporterDown': ([('up{job="site",instance="%s"}' % RMS, '0x10')], '4m', {'job': 'site', 'instance': RMS}, 'blackbox không phản hồi'),
    'BackupStale': ([('rms_backup_last_success_timestamp_seconds{kind="daily",instance="may1-prod"}', '0x1700')], '27h', {'kind': 'daily', 'instance': 'may1-prod'}, 'bản daily cũ quá 26 giờ'),
    'OffsiteBackupStale': ([('rms_backup_last_success_timestamp_seconds{kind="daily",instance="may1-prod"}', '0x60')], '40m', {'kind': 'offsite'}, 'chưa từng có bản offsite (absent)'),
    'WalArchivingFailing': ([('cnpg_pg_stat_archiver_last_failed_time{job="cnpg",instance="10.10.0.1:9182"}', '200x10'),
                             ('cnpg_pg_stat_archiver_last_archived_time{job="cnpg",instance="10.10.0.1:9182"}', '100x10')], '7m', {'job': 'cnpg', 'instance': '10.10.0.1:9182'}, 'lần đẩy WAL gần nhất lỗi'),
    'PostgresExporterDown': ([('up{job="cnpg",instance="10.10.0.1:9182"}', '0x10')], '7m', {'job': 'cnpg', 'instance': '10.10.0.1:9182'}, 'mất số liệu CloudNativePG'),
    'SealedKeyBackupStale': ([('rms_backup_last_success_timestamp_seconds{kind="sealed-key",instance="may1-prod"}', '0x11800')], '195h', {'kind': 'sealed-key', 'instance': 'may1-prod'}, 'khóa sealed cũ quá 8 ngày'),
    'ArgoCDDown': ([('up{job="argocd",instance="10.43.0.82:8082"}', '0x10')], '7m', {'job': 'argocd', 'instance': '10.43.0.82:8082'}, 'mất số liệu Argo CD'),
    'ArgoAppOutOfSync': ([('argocd_app_info{name="rms-prod",sync_status="OutOfSync",health_status="Healthy"}', '1x20')], '17m',
                         {'name': 'rms-prod', 'sync_status': 'OutOfSync', 'health_status': 'Healthy'}, 'lệch Git 15 phút'),
    'ArgoAppUnhealthy': ([('argocd_app_info{name="rms-prod",sync_status="Synced",health_status="Degraded"}', '1x20')], '17m',
                         {'name': 'rms-prod', 'sync_status': 'Synced', 'health_status': 'Degraded'}, 'Degraded 15 phút'),
    'ArgoSyncFailed': ([('argocd_app_sync_total{name="rms-prod",phase="Failed"}', '0 0 1 1 1')], '3m', {'name': 'rms-prod', 'phase': 'Failed'}, 'một lần đồng bộ thất bại'),
    'ArgoWebhookRejected': ([('rms_argocd_webhook_rejected_10m{instance="may2-mgmt"}', '2x10'),
                             ('rms_argocd_webhook_check_timestamp_seconds{instance="may2-mgmt"}', '0+60x10')], '7m', {'instance': 'may2-mgmt'}, 'GitHub gửi webhook sai chữ ký'),
    'ToolDown': ([('probe_success{job="tool",instance="https://grafana.atlasplatform12.tech/api/health"}', '0x10')], '7m',
                 {'job': 'tool', 'instance': 'https://grafana.atlasplatform12.tech/api/health'}, 'Grafana không trả 200'),
    'NodeExporterDown': ([('up{job="node",instance="may2-mgmt"}', '0x10')], '4m', {'job': 'node', 'instance': 'may2-mgmt'}, 'mất số liệu máy 2'),
    'DiskAlmostFull': ([('node_filesystem_avail_bytes{mountpoint="/",instance="may1-prod",fstype="ext4"}', '10x10'),
                        ('node_filesystem_size_bytes{mountpoint="/",instance="may1-prod",fstype="ext4"}', '100x10')], '7m',
                       {'mountpoint': '/', 'instance': 'may1-prod', 'fstype': 'ext4'}, 'đĩa còn 10%'),
    'HighMemory': ([('node_memory_MemAvailable_bytes{instance="may1-prod"}', '5x20'),
                    ('node_memory_MemTotal_bytes{instance="may1-prod"}', '100x20')], '12m', {'instance': 'may1-prod'}, 'RAM dùng 95%'),
    'HighCpu': ([('node_cpu_seconds_total{instance="may1-prod",cpu="0",mode="idle"}', '0+3x20')], '16m', {'instance': 'may1-prod'}, 'CPU rảnh 5%'),
}
# Ca KHÔNG được bật: trạng thái bình thường, hoặc sự cố chưa kéo dài hết "for"
KHONG_BAT = [
    ('AppDown', [('up{job="rms-app",instance="pod-a"}', '0x10'), ('up{job="rms-app",instance="pod-b"}', '1x10')], '5m', 'còn 1 pod trả số liệu'),
    ('SiteDown', [('probe_success{job="site",instance="%s"}' % RMS, '0 0 1 1 1 1')], '5m', 'lỗi 1 phút rồi tự hết (chưa đủ 2 phút)'),
    ('HighErrorRate', [('http_server_requests_seconds_count{status="500",uri="/api/orders"}', '0+1x20'),
                       ('http_server_requests_seconds_count{status="200",uri="/api/orders"}', '0+99x20')], '12m', '1% lỗi'),
    ('BackupStale', [('rms_backup_last_success_timestamp_seconds{kind="daily",instance="may1-prod"}', '0+60x1700')], '27h', 'sao lưu đều đặn'),
    ('WalArchivingFailing', [('cnpg_pg_stat_archiver_last_failed_time{job="cnpg",instance="10.10.0.1:9182"}', '100x10'),
                             ('cnpg_pg_stat_archiver_last_archived_time{job="cnpg",instance="10.10.0.1:9182"}', '200x10')], '7m', 'đã đẩy lại thành công sau lần lỗi'),
    ('DiskAlmostFull', [('node_filesystem_avail_bytes{mountpoint="/",instance="may1-prod",fstype="ext4"}', '64x10'),
                        ('node_filesystem_size_bytes{mountpoint="/",instance="may1-prod",fstype="ext4"}', '100x10')], '7m', 'đĩa còn 64%'),
]


def render(text, labels):
    return re.sub(r'\{\{\s*\$labels\.(\w+)\s*\}\}', lambda m: labels.get(m.group(1), ''), text)


groups = yaml.safe_load(open(RULES, encoding='utf-8'))['groups']
rules = {r['alert']: r for g in groups for r in g['rules'] if 'alert' in r}
thieu = sorted(set(rules) - set(BAT))
if thieu:
    sys.exit('Luật chưa có ca test: ' + ', '.join(thieu))

tests = []
for name, r in rules.items():
    series, t, lab, note = BAT[name]
    exp_labels = dict(lab, **r.get('labels', {}))
    tests.append({'name': f'{name} bật: {note}',
                  'interval': '1m',
                  'input_series': [{'series': s, 'values': v} for s, v in series] or [{'series': 'khong_co{}', 'values': '0'}],
                  'alert_rule_test': [{'eval_time': t, 'alertname': name,
                                       'exp_alerts': [{'exp_labels': exp_labels,
                                                       'exp_annotations': {k: render(v, lab) for k, v in r.get('annotations', {}).items()}}]}]})
for name, series, t, note in KHONG_BAT:
    tests.append({'name': f'{name} không bật: {note}', 'interval': '1m',
                  'input_series': [{'series': s, 'values': v} for s, v in series],
                  'alert_rule_test': [{'eval_time': t, 'alertname': name, 'exp_alerts': []}]})

with open(OUT, 'w', encoding='utf-8') as f:
    f.write('# SINH TỰ ĐỘNG bởi gen_alerts_test.py, không sửa tay. Chạy: bash scripts/demo/test-canh-bao.sh\n')
    yaml.safe_dump({'rule_files': ['../alerts.yml'], 'evaluation_interval': '1m', 'tests': tests},
                   f, allow_unicode=True, sort_keys=False, width=200)
print(f'{OUT}: {len(BAT)} luật, {len(tests)} ca test')
