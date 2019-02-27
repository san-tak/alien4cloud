define(function (require) {
  'use strict';

  var modules = require('modules');
  var _ = require('lodash');

  modules.get('a4c-catalog', ['ui.bootstrap']).controller('NewArchiveTemplateCtrl', ['$scope', '$uibModalInstance', 'applicationVersionServices', 'topology',
    function($scope, $uibModalInstance, applicationVersionServices, topology) {
      $scope.topology = topology;
      $scope.topologytemplate = {
        version: '0.1.0-SNAPSHOT'
      };
      if(_.defined(topology)) {
        $scope.topologytemplate.name = topology.archiveName;
      }
      $scope.versionPattern = applicationVersionServices.pattern;
      $scope.create = function(valid) {
        if (valid) {
          if(topology) {
            $scope.topologytemplate.fromTopologyId = topology.id;
          }
          $uibModalInstance.close($scope.topologytemplate);
        }
      };
      $scope.cancel = function() {
        $uibModalInstance.dismiss('cancel');
      };
    }
  ]);
});
