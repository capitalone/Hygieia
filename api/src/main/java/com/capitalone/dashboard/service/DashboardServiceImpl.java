package com.capitalone.dashboard.service;

import com.capitalone.dashboard.model.*;
import com.capitalone.dashboard.repository.*;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final DashboardRepository dashboardRepository;
    private final ComponentRepository componentRepository;
    private final CollectorRepository collectorRepository;
    private final CollectorItemRepository collectorItemRepository;
    private final ServiceRepository serviceRepository;

    @Autowired
    public DashboardServiceImpl(DashboardRepository dashboardRepository,
                                ComponentRepository componentRepository,
                                CollectorRepository collectorRepository,
                                CollectorItemRepository collectorItemRepository,
                                ServiceRepository serviceRepository) {
        this.dashboardRepository = dashboardRepository;
        this.componentRepository = componentRepository;
        this.collectorRepository = collectorRepository;
        this.collectorItemRepository = collectorItemRepository;
        this.serviceRepository = serviceRepository;
    }

    @Override
    public Iterable<Dashboard> all() {
        return dashboardRepository.findAll(new Sort(Sort.Direction.ASC, "title"));
    }

    @Override
    public Dashboard get(ObjectId id) {
        Dashboard dashboard = dashboardRepository.findOne(id);

        if (!dashboard.getApplication().getComponents().isEmpty()) {
            // Add transient Collector instance to each CollectorItem
            Map<CollectorType, List<CollectorItem>> itemMap = dashboard.getApplication().getComponents().get(0).getCollectorItems();

            Iterable<Collector> collectors = collectorsFromItems(itemMap);

            for (List<CollectorItem> collectorItems : itemMap.values()) {
                for (CollectorItem collectorItem : collectorItems) {
                    collectorItem.setCollector(getCollector(collectorItem.getCollectorId(), collectors));
                }
            }
        }

        return dashboard;
    }

    @Override
    public Dashboard create(Dashboard dashboard) {
        componentRepository.save(dashboard.getApplication().getComponents());
        return dashboardRepository.save(dashboard);
    }

    @Override
    public Dashboard update(Dashboard dashboard) {
        return create(dashboard);
    }

    @Override
    public void delete(ObjectId id) {
        Dashboard dashboard = dashboardRepository.findOne(id);
        componentRepository.delete(dashboard.getApplication().getComponents());

        // Remove this Dashboard's services and service dependencies
        serviceRepository.delete(serviceRepository.findByDashboardId(id));
        for (com.capitalone.dashboard.model.Service service : serviceRepository.findByDependedBy(id)) {
            service.getDependedBy().remove(id);
            serviceRepository.save(service);
        }

        dashboardRepository.delete(dashboard);
    }

    @Override
    public Component associateCollectorToComponent(ObjectId componentId, List<ObjectId> collectorItemIds) {
        if (componentId == null || collectorItemIds == null) {
            // Not all widgets gather data from collectors
            return null;
        }

        Component component = componentRepository.findOne(componentId);
        //we can not assume what collector item is added, what is removed etc so, we will
        //refresh the association. First disable all collector items, then remove all and re-add
        Map<CollectorType, List<CollectorItem>> existingCollectorItems = component.getCollectorItems();
        for (Map.Entry<CollectorType, List<CollectorItem>> entry : existingCollectorItems.entrySet()) {
            List<CollectorItem> cItems = entry.getValue();
            if (!CollectionUtils.isEmpty(cItems)) {
                for (CollectorItem ci : cItems) {
                    ci.setEnabled(false);
                    collectorItemRepository.save(ci);
                }
            }
        }
        //Now remove all collector items
        component.getCollectorItems().clear();

        //Last step: add collector items that came in
        for (ObjectId collectorItemId : collectorItemIds) {
            CollectorItem collectorItem = collectorItemRepository.findOne(collectorItemId);
            Collector collector = collectorRepository.findOne(collectorItem.getCollectorId());

            component.addCollectorItem(collector.getCollectorType(), collectorItem);

            if (!collectorItem.isEnabled()) {
                collectorItem.setEnabled(true);
                collectorItemRepository.save(collectorItem);
            }

            // set transient collector property
            collectorItem.setCollector(collector);
        }

        componentRepository.save(component);
        return component;
    }

    @Override
    public Widget addWidget(Dashboard dashboard, Widget widget) {
        widget.setId(ObjectId.get());
        dashboard.getWidgets().add(widget);
        dashboardRepository.save(dashboard);
        return widget;
    }

    @Override
    public Widget getWidget(Dashboard dashboard, ObjectId widgetId) {
        return Iterables.find(dashboard.getWidgets(), new WidgetByIdPredicate(widgetId));
    }

    @Override
    public Widget updateWidget(Dashboard dashboard, Widget widget) {
        int index = dashboard.getWidgets().indexOf(widget);
        dashboard.getWidgets().set(index, widget);
        dashboardRepository.save(dashboard);
        return widget;
    }

    private static final class WidgetByIdPredicate implements Predicate<Widget> {
        private final ObjectId widgetId;

        private WidgetByIdPredicate(ObjectId widgetId) {
            this.widgetId = widgetId;
        }

        @Override
        public boolean apply(Widget widget) {
            return widget.getId().equals(widgetId);
        }
    }

    private Iterable<Collector> collectorsFromItems(Map<CollectorType, List<CollectorItem>> itemMap) {
        Set<ObjectId> collectorIds = new HashSet<>();
        for (List<CollectorItem> collectorItems : itemMap.values()) {
            for (CollectorItem collectorItem : collectorItems) {
                collectorIds.add(collectorItem.getCollectorId());
            }
        }

        return collectorRepository.findAll(collectorIds);
    }

    private Collector getCollector(final ObjectId collectorId, Iterable<Collector> collectors) {
        return Iterables.tryFind(collectors, new Predicate<Collector>() {
            @Override
            public boolean apply(Collector collector) {
                return collector.getId().equals(collectorId);
            }
        }).orNull();
    }

	@Override
	public List<Dashboard> getOwnedDashboards(String owner) {
		
		List<Dashboard> myDashboard=dashboardRepository.findByOwner(owner);
		
		return myDashboard;
	}

	@Override
	public String getDashboardOwner(String dashboardName) {
		
		
		String dashboardOwner=dashboardRepository.findByTitle(dashboardName).get(0).getOwner();
		
		return dashboardOwner;
	}
}
