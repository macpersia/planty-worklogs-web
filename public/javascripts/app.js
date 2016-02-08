var app = angular.module('planty-worklogs-ng', []);
app.controller('reportParamsController', function($scope, $http) {
    $http.get("/initParams").success(function(response) {
        $scope.reportParams = response;
        $scope.reportParams.tzOffsetMinutes = new Date().getTimezoneOffset();
    });
    $scope.submitParams = function(e) {
        angular.element($('#worklogsTable')).scope().retrieveWorklogs(e, $scope.reportParams);
    };
});
app.controller('worklogsController', function($scope, $http) {
    $scope.worklogs = [];
    $scope.retrieveWorklogs = function(e, reportParams) {
        $(e.target).toggleClass('active');

        $http.post('/worklogs', reportParams, {})
        .then(function(response) {
            $scope.worklogs = response.data.entries;
            $(e.target).toggleClass('active');

        }, function(response) {
            $(e.target).toggleClass('active');
        });
    };
    $scope.calcTotalDuration = function() {
        var total = 0;
        for(var i = 0; i < $scope.worklogs.length; i++){
            total += $scope.worklogs[i].duration;
        }
        return total;
    }
});

