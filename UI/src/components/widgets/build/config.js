/**
 * Build widget configuration
 */
(function () {
    'use strict';
    angular
        .module(HygieiaConfig.module)
        .controller('BuildWidgetConfigController', BuildWidgetConfigController);
    BuildWidgetConfigController.$inject = ['modalData', '$scope', 'collectorData', '$uibModalInstance'];
    function BuildWidgetConfigController(modalData, $scope, collectorData, $uibModalInstance) {
        var ctrl = this,
        widgetConfig = modalData.widgetConfig;
        
        // public variables
        ctrl.buildDurationThreshold = 3;
        ctrl.buildConsecutiveFailureThreshold = 5;
        
        $scope.getJobs = function (filter) {
        	return collectorData.itemsByType('build', {"search": filter, "size": 20}).then(function (response){
        		return response;
        	});
        }

        $scope.getJobsById = function (id) {
            return collectorData.getCollectorItemById(id).then(function (response){
                return response;
            });
        }
        loadSavedBuildJob();
        // set values from config
        if (widgetConfig) {
            if (widgetConfig.options.buildDurationThreshold) {
                ctrl.buildDurationThreshold = widgetConfig.options.buildDurationThreshold;
            }
            if (widgetConfig.options.consecutiveFailureThreshold) {
                ctrl.buildConsecutiveFailureThreshold = widgetConfig.options.consecutiveFailureThreshold;
            }
        }
        // public methods
        ctrl.submit = submitForm;

        // method implementations
        function loadSavedBuildJob(){
            ctrl.buildId ="";
            // get the job id from the widget config, not global
            var collectorItemId = modalData.widgetConfig.collectorItemIds[0];

            if(collectorItemId) {
                ctrl.buildId = collectorItemId;
            	$scope.getJobsById(ctrl.buildId).then(getBuildsCallback)
            }
        }
        
        function getBuildsCallback(data) {
            ctrl.collectorItemId = data;
        }

        function submitForm(valid, collector) {
            if (valid) {
                var form = document.buildConfigForm;
                var postObj = {
                    name: modalData.title,
                    type: 'build',
                    options: {
                    	id: widgetConfig.options.id,
                        buildDurationThreshold: parseFloat(form.buildDurationThreshold.value),
                        consecutiveFailureThreshold: parseFloat(form.buildConsecutiveFailureThreshold.value)
                    },
                    componentId: modalData.dashboard.application.components[0].id,
                    collectorItemId: collector.id,
                };
                // pass this new config to the modal closing so it's saved
                $uibModalInstance.close(postObj);
            }
        }
    }
})();
