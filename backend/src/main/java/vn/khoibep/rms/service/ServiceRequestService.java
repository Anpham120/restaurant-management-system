package vn.khoibep.rms.service;

import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.common.realtime.RealtimeEvents;
import vn.khoibep.rms.common.security.CurrentUser;
import vn.khoibep.rms.dto.OrderDtos.ServiceRequestDto;
import vn.khoibep.rms.model.DiningTable;
import vn.khoibep.rms.model.ServiceRequest;
import vn.khoibep.rms.repository.ServiceRequestRepository;

/** The waiters' side of guest calls (FR-06.6, FR-06.7). The guest side is in {@link GuestOrderService}. */
@Service
@RequiredArgsConstructor
public class ServiceRequestService {

    private final ServiceRequestRepository requests;
    private final CurrentUser currentUser;
    private final RealtimeEvents realtime;

    /** Waiting calls, oldest first. */
    @Transactional(readOnly = true)
    public List<ServiceRequestDto> open() {
        return requests.findOpen().stream().map(ServiceRequestDto::from).toList();
    }

    /** FR-06.7, BR-29: one waiter takes the call; who and when are kept to measure the response time. */
    @Transactional
    public void take(Long id) {
        ServiceRequest request = requests.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu"));
        if (!request.isOpen()) {
            throw ApiException.conflict("Đã có nhân viên nhận yêu cầu này");
        }
        request.handle(currentUser.id(), Instant.now());
        DiningTable table = request.getTable();
        realtime.requestsChanged(table.getId(), table.getQrToken(), null);
    }
}
