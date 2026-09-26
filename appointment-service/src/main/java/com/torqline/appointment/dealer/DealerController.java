package com.torqline.appointment.dealer;

import com.torqline.common.domain.ServiceType;
import com.torqline.common.domain.VehicleType;
import com.torqline.common.web.ApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dealers")
public class DealerController {

    public record BayView(Long id, String name, VehicleType vehicleType) {
    }

    public record DealerView(String id, String name, String city, String timezone, LocalTime openTime,
                             LocalTime closeTime, Map<VehicleType, Long> bayCount) {
    }

    public record ServiceTypeView(ServiceType code, String description, Map<VehicleType, Long> durationMinutes) {
    }

    private final DealerRepository dealers;
    private final ServiceBayRepository bays;

    public DealerController(DealerRepository dealers, ServiceBayRepository bays) {
        this.dealers = dealers;
        this.bays = bays;
    }

    @GetMapping
    public List<DealerView> list() {
        return dealers.findAll().stream().map(this::view).toList();
    }

    @GetMapping("/{dealerId}")
    public DealerView get(@PathVariable String dealerId) {
        return view(dealers.findById(dealerId).orElseThrow(() -> ApiException.notFound("Dealer", dealerId)));
    }

    @GetMapping("/{dealerId}/bays")
    public List<BayView> bays(@PathVariable String dealerId) {
        return bays.findByDealerIdOrderById(dealerId).stream()
                .map(b -> new BayView(b.getId(), b.getName(), b.getVehicleType())).toList();
    }

    /** Service catalogue with per-vehicle durations, so a client can show only jobs that apply to a bike or a car. */
    @GetMapping("/service-types")
    public List<ServiceTypeView> serviceTypes() {
        return Arrays.stream(ServiceType.values())
                .map(t -> new ServiceTypeView(t, t.description(), Arrays.stream(VehicleType.values())
                        .filter(t::supports)
                        .collect(Collectors.toMap(v -> v, v -> t.durationFor(v).toMinutes()))))
                .toList();
    }

    private DealerView view(Dealer d) {
        Map<VehicleType, Long> count = bays.findByDealerIdOrderById(d.getId()).stream()
                .collect(Collectors.groupingBy(ServiceBay::getVehicleType, Collectors.counting()));
        return new DealerView(d.getId(), d.getName(), d.getCity(), d.zone().getId(), d.getOpenTime(),
                d.getCloseTime(), count);
    }
}
