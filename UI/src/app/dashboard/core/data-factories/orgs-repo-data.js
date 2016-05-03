/**
 * Gets orgs repo related data
 */
(function () {
    'use strict';

    angular
        .module(HygieiaConfig.module + '.core')
        .factory('orgsRepoData', orgsRepoData);

    function orgsRepoData($http) {
        var testDetailRoute = 'test-data/commit_detail.json';
        var orgRepoDetailRoute = '/api/repos';

        return {
            details: details
        };

        // get 15 days worth of commit data for the component
        function details(params) {
            return $http.get(HygieiaConfig.local ? testDetailRoute : orgRepoDetailRoute, { params: params })
                .then(function (response) {
                    return response.data;
                });
        }
    }
})();