/**
*  Service that provides functionalities to edit nodes in a topology.
*/
define(function (require) {
  'use strict';

  var angular = require('angular');
  var modules = require('modules');

  modules.get('a4c-topology-editor').controller('quickRelationshipSearchModal', [ '$uibModalInstance', 'relationshipTypeQuickSearchService', 'relationshipType',
    function( $uibModalInstance, relationshipTypeQuickSearchService, relationshipType) {
      var $ctrl = this;
      $ctrl.relationshipTypeQuickSearchHandler = {
        'doQuickSearch': relationshipTypeQuickSearchService.doQuickSearch,
        'waitBeforeRequest': 500,
        'minLength': 3
      };
      $ctrl.selectedRelationship = { 'type': relationshipType };
      $ctrl.updateRelationshipType = function(newType) {
        $ctrl.selectedRelationship.type = newType;
      };
      $ctrl.ok = function(){
        $uibModalInstance.close($ctrl.selectedRelationship.type);
      };
      $ctrl.cancel = function(){
        $uibModalInstance.dismiss('close');
      };
    }]);

  modules.get('a4c-topology-editor').factory('topoEditSubstitution', [ 'topologyServices', 'suggestionServices', '$state', '$uibModal',
    function(topologyServices, suggestionServices, $state, $uibModal) {
      var TopologyEditorMixin = function(scope) {
        this.scope = scope;
      };

      TopologyEditorMixin.prototype = {
        constructor: TopologyEditorMixin,
        /** Init method is called when the controller is ready. */
        init: function() {},

        refresh: function() {
          // about substitution
          if (this.scope.topology.topology.substitutionMapping && this.scope.topology.topology.substitutionMapping.substitutionType) {
            this.scope.substitutionType = this.scope.topology.topology.substitutionMapping.substitutionType;
          } else {
            this.scope.substitutionType = undefined;
          }
        },

        selectType: function(substitutionType) {
          var self = this;
          if (!this.scope.topology.topology.substitutionMapping || this.scope.topology.topology.substitutionMapping.substitutionType !== substitutionType) {
            this.scope.execute({
              type: 'org.alien4cloud.tosca.editor.operations.substitution.AddSubstitutionTypeOperation',
              elementId: substitutionType
            }, function(result) {
              if (!result.error) {
                self.scope.refreshTopology(result.data);
              }
            });
          }
        },

        remove: function() {
          var self = this;
          this.scope.execute({
            type: 'org.alien4cloud.tosca.editor.operations.substitution.RemoveSubstitutionTypeOperation'
          }, function(result) {
            if (!result.error) {
              self.scope.refreshTopology(result.data);
            }
          });
        },

        isTypeInDependencies: function(nodeType) {
          for (var i=0; i< this.scope.topology.topology.dependencies.length; i++) {
            if (this.scope.topology.topology.dependencies[i].name === nodeType.archiveName) {
              return true;
            }
          }
          return false;
        },

        exposeCapability: function(capabilityId) {
          var self = this;
          if (this.isCapabilityExposed(capabilityId)) {
            return;
          }
          this.scope.execute({
            type: 'org.alien4cloud.tosca.editor.operations.substitution.AddCapabilitySubstitutionTypeOperation',
            nodeTemplateName: self.scope.selectedNodeTemplate.name,
            substitutionCapabilityId: capabilityId,
            capabilityId: capabilityId
          }, function(result) {
            if (!result.error) {
              self.scope.refreshTopology(result.data);
            }
          });
        },

        isCapabilityExposed: function(capabilityId) {
          var instance = this;
          var result = false;
          angular.forEach(instance.scope.topology.topology.substitutionMapping.capabilities, function(value) {
            if (value.nodeTemplateName === instance.scope.selectedNodeTemplate.name && value.targetId === capabilityId) {
              result = true;
            }
          });
          return result;
        },

        updateCababilityKey: function(oldKey, newKey) {
          var self = this;
          this.scope.execute({
            type: 'org.alien4cloud.tosca.editor.operations.substitution.UpdateCapabilitySubstitutionTypeOperation',
            substitutionCapabilityId: oldKey,
            newCapabilityId: newKey
          }, function(result) {
            if (!result.error) {
              self.scope.refreshTopology(result.data);
            }
          });
        },

        removeCabability: function(key) {
          var self = this;
          this.scope.execute({
            type: 'org.alien4cloud.tosca.editor.operations.substitution.RemoveCapabilitySubstitutionTypeOperation',
            substitutionCapabilityId: key
          }, function(result) {
            if (!result.error) {
              self.scope.refreshTopology(result.data);
            }
          });
        },

        exposeRequirement: function(requirementId) {
          var self = this;
          if (this.isRequirementExposed(requirementId)) {
            return;
          }
          this.scope.execute({
            type: 'org.alien4cloud.tosca.editor.operations.substitution.AddRequirementSubstitutionTypeOperation',
            nodeTemplateName: self.scope.selectedNodeTemplate.name,
            substitutionRequirementId: requirementId,
            requirementId: requirementId
          }, function(result) {
            if (!result.error) {
              self.scope.refreshTopology(result.data);
            }
          });
        },

        isRequirementExposed: function(requirementId) {
          var instance = this;
          var result = false;
          angular.forEach(instance.scope.topology.topology.substitutionMapping.requirements, function(value) {
            if (value.nodeTemplateName === instance.scope.selectedNodeTemplate.name && value.targetId === requirementId) {
              result = true;
            }
          });
          return result;
        },

        updateRequirementKey: function(oldKey, newKey) {
          var self = this;
          this.scope.execute({
            type: 'org.alien4cloud.tosca.editor.operations.substitution.UpdateRequirementSubstitutionTypeOperation',
            substitutionRequirementId: oldKey,
            newRequirementId: newKey
          }, function(result) {
            if (!result.error) {
              self.scope.refreshTopology(result.data);
            }
          });
        },

        removeRequirement: function(key) {
          var self = this;
          this.scope.execute({
            type: 'org.alien4cloud.tosca.editor.operations.substitution.RemoveRequirementSubstitutionTypeOperation',
            substitutionRequirementId: key
          }, function(result) {
            if (!result.error) {
              self.scope.refreshTopology(result.data);
            }
          });
        },

        displayEmbededTopology: function(topologyId) {
          var tokens = topologyId.trim().split(':');
          if (tokens.length > 1) {
            var archiveName = tokens[0];
            var archiveVersion = tokens[1];
            $state.go('topologycatalog.csar', { archiveName: archiveName, archiveVersion: archiveVersion });
          }
        },

        cfgCapaSrvRelationship: function(key) {
          var self = this;
          var currentRelationshipType = self.scope.topology.topology.substitutionMapping.capabilities[key].serviceRelationshipType;
          var modalInstance = $uibModal.open({
            templateUrl: 'views/topology/editor_service_relationship_modal.html',
            controller: 'quickRelationshipSearchModal',
            controllerAs: '$ctrl',
            resolve: {relationshipType : function() {return currentRelationshipType;}}
          });

          modalInstance.result.then(function(relationshipTypeId) {
            var relationshipTypeSplit = relationshipTypeId.split(':');
            self.scope.execute({
              type: 'org.alien4cloud.tosca.editor.operations.substitution.SetSubstitutionCapabilityServiceRelationshipOperation',
              substitutionCapabilityId: key,
              relationshipType: relationshipTypeSplit[0],
              relationshipVersion: relationshipTypeSplit[1]
            }, function(result) {
              if (!result.error) {
                self.scope.refreshTopology(result.data);
              }
            });
          });
        },

        cfgReqSrvRelationship: function(key) {
          var self = this;
          var currentRelationshipType = self.scope.topology.topology.substitutionMapping.requirements[key].serviceRelationshipType;
          var modalInstance = $uibModal.open({
            templateUrl: 'views/topology/editor_service_relationship_modal.html',
            controller: 'quickRelationshipSearchModal',
            controllerAs: '$ctrl',
            resolve: {relationshipType : function() {return currentRelationshipType;}}
          });

          modalInstance.result.then(function(relationshipTypeId) {
            var relationshipTypeSplit = relationshipTypeId.split(':');
            self.scope.execute({
              type: 'org.alien4cloud.tosca.editor.operations.substitution.SetSubstitutionRequirementServiceRelationshipOperation',
              substitutionRequirementId: key,
              relationshipType: relationshipTypeSplit[0],
              relationshipVersion: relationshipTypeSplit[1]
            }, function(result) {
              if (!result.error) {
                self.scope.refreshTopology(result.data);
              }
            });
          });
        }
      };

      return function(scope) {
        var instance = new TopologyEditorMixin(scope);
        scope.substitution = instance;
        scope.substitution.getTypeSuggestions = {
          get: suggestionServices.abstractNodetypeSuggestions,
          waitBeforeRequest: 0, // TODO this seems unused...
          minLength: 2
        };
      };
    }
  ]); // modules
}); // define
