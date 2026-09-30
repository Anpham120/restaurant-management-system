package vn.bnn.rms.order;

import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.bnn.rms.menu.MenuDtos.MenuSectionDto;
import vn.bnn.rms.menu.MenuService;
import vn.bnn.rms.order.OrderDtos.AddItemsRequest;
import vn.bnn.rms.order.OrderDtos.GuestTableDto;
import vn.bnn.rms.payment.PaymentDtos.PaymentInstruction;

/** Guest API behind the table QR code. No login; the unguessable token is the key (BR-09, BR-11). */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final GuestOrderService guestOrderService;
    private final MenuService menuService;

    @GetMapping("/tables/{token}")
    public GuestTableDto table(@PathVariable String token) {
        return guestOrderService.view(token);
    }

    @GetMapping("/menu")
    public List<MenuSectionDto> menu() {
        return menuService.guestMenu();
    }

    @PostMapping("/tables/{token}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public GuestTableDto addItems(@PathVariable String token, @Valid @RequestBody AddItemsRequest request) {
        return guestOrderService.addItems(token, request);
    }

    @PostMapping("/tables/{token}/payment")
    public PaymentInstruction requestPayment(@PathVariable String token) {
        return guestOrderService.requestPayment(token);
    }
}
