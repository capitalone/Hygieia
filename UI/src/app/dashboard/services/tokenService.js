/**
 * Service to handle all token operations
*/
(function () {
    'use strict';

    angular
        .module(HygieiaConfig.module)
        .service('tokenService', tokenService);

    tokenService.$inject = ['$window'];
    function tokenService($window) {
        this.setToken = function (token) {
          $window.localStorage.token = token;
        }

        this.getToken = function () {
          return $window.localStorage.token;
        }

        this.removeToken = function () {
          $window.localStorage.removeItem('token');
        }
    }
})();
