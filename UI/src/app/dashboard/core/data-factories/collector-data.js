/**
 * Collector and collector item data
 */
(function () {
    'use strict';

    angular
        .module(HygieiaConfig.module + '.core')
        .factory('collectorData', collectorData);

    function collectorData($http, $q) {
        var itemRoute = '/api/collector/item';
        var itemByComponentRoute = '/api/collector/item/component/';
        var itemsByTypeRoute = '/api/collector/item/type/';
        var collectorsByTypeRoute = '/api/collector/type/';
        var encryptRoute = "/api/encrypt/";
        var collectorByIdRoute = "/api/collector/collectorId/";
        var testitemsByTypeRoute = 'test-data/collector-item-type.json';
        var testitemByComponentRoute = 'test-data/collector-item-component.json';
        var caStaticDetailsRoute = '/api/quality/static-analysis';
        var caSecDetailsRoute = '/api/quality/security-analysis';

        return {
            itemsByType: itemsByType,
            createCollectorItem: createCollectorItem,
            getCollectorItem : getCollectorItem,
            collectorsByType: collectorsByType,
            encrypt: encrypt,
            getCollectorItemById:getCollectorItemById,
            collectorsById:collectorsById

        };

        function getCollectorItemById(id) {
            return $http.get(itemRoute + '/'+id).then(function (response) {
                return response.data;
            });
        }

        function itemsByType(type, params) {
            return $http.get(HygieiaConfig.local ? testitemsByTypeRoute : itemsByTypeRoute + type, {params: params}).then(function (response) {
                return response.data;
            });
        }

        function createCollectorItem(collectorItem) {
            return $http.post(itemRoute, collectorItem);
        }


        function getCollectorItem(item, type) {
            return $http.get(HygieiaConfig.local ? testitemByComponentRoute : itemByComponentRoute + item + '?type=' + type).then(function (response) {
                return response.data;
            });
        }

        function collectorsByType(type) {
            return $http.get(collectorsByTypeRoute + type).then(function (response) {
                return response.data;
            });
        }

        function collectorsById(id) {
            return $http.get(collectorByIdRoute + id).then(function (response) {
                return response.data;
            });
        }

        function encrypt(message) {
            var submitData = {
                message : message
            }
            return $http.post(encryptRoute ,submitData).then(function (response) {
                return response.data;
            });
        }
    }
})();
