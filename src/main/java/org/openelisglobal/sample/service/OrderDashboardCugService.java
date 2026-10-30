package org.openelisglobal.sample.service;

import java.util.List;
import org.openelisglobal.common.rest.provider.bean.homedashboard.OrderDisplayBean;

public interface OrderDashboardCugService {
    void populateOrderCugs(List<OrderDisplayBean> orders);
}
