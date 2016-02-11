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
    $scope.matches = [];
    $scope.retrieveWorklogs = function(e, reportParams) {
        $(e.target).toggleClass('active');

        $http.post('/worklogs', reportParams, {})
        .then(function(response) {
            $scope.matches = response.data.matches;
            $(e.target).toggleClass('active');

        }, function(response) {
            $(e.target).toggleClass('active');
        });
    };
    $scope.calcTotalDurationInJira = function() {
        var total = 0;
        for(var i = 0; i < $scope.matches.length; i++) {
            var duration = $scope.matches[i].durationInJira;
            if (duration)
                total += duration;
        }
        return total;
    }
    $scope.calcTotalDurationInCats = function() {
        var total = 0;
        for(var i = 0; i < $scope.matches.length; i++) {
            var duration = $scope.matches[i].durationInCats;
            if (duration)
                total += duration;
        }
        return total;
    }
});

