package com.torqline.repairorder.dto;

import com.torqline.repairorder.order.PartLineStatus;

import java.math.BigDecimal;

public record PartLineView(String sku, String name, int quantity, BigDecimal unitPrice, PartLineStatus status) {
}
