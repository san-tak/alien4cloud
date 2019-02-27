define(function (require) {
  'use strict';

  var modules = require('modules');
  var _ = require('lodash');

  modules.get('a4c-topology-editor').factory('topologyVariableService', [
    function() {

      function getNode(parent, fullPath) {
        var currentNode = parent;
        var part, parts = fullPath.split('/');
        for(var i=0;i<parts.length;i++) {
          part = parts[i];
          if(part.length === 0) {
            continue;
          }
          currentNode = _.find(currentNode.children, {name:part});
          if(_.undefined(currentNode)) {
            break;
          }
        }
        return currentNode;
      }

      return {
        // end
        getInputsNode: function(expanded) {return getNode(expanded, 'inputs/inputs.yml');}
      };
    }
  ]);
});
