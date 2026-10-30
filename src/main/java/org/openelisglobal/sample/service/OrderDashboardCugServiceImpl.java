package org.openelisglobal.sample.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.rest.provider.bean.homedashboard.OrderDisplayBean;
import org.openelisglobal.sampleitem.dao.SampleItemDAO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderDashboardCugServiceImpl implements OrderDashboardCugService {
    private final SampleItemDAO sampleItems;

    public OrderDashboardCugServiceImpl(SampleItemDAO sampleItems) {
        this.sampleItems = sampleItems;
    }

    @Override
    public void populateOrderCugs(List<OrderDisplayBean> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        List<String> numbers = orders.stream().map(OrderDisplayBean::getLabNumber).filter(StringUtils::isNotBlank)
                .distinct().toList();
        if (numbers.isEmpty()) {
            return;
        }
        Map<String, Set<String>> codes = new LinkedHashMap<>();
        for (Object[] row : sampleItems.findCugCodesByAccessionNumbers(numbers)) {
            String code = StringUtils.trimToEmpty((String) row[1]);
            if (!code.isEmpty()) {
                codes.computeIfAbsent((String) row[0], ignored -> new LinkedHashSet<>()).add(code);
            }
        }
        for (OrderDisplayBean order : orders) {
            order.setCugCode(String.join(", ", codes.getOrDefault(order.getLabNumber(), Set.of())));
        }
    }
}
