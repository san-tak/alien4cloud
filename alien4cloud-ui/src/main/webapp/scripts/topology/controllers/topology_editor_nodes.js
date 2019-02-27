/**
*  Service that provides functionalities to edit nodes in a topology.
*/
define(function (require) {
  'use strict';
  var modules = require('modules');
  var angular = require('angular');
  var _ = require('lodash');
  var $ = require('jquery');

  require('scripts/common/controllers/confirm_modal');

  modules.get('a4c-topology-editor').factory('topoEditNodes', ['toscaService', '$filter', '$uibModal', '$translate', 'nodeTemplateService',
    function(toscaService, $filter, $uibModal, $translate, nodeTemplateService) {
      var nodeNamePattern = '^\\w+$';

      var TopologyEditorMixin = function(scope) {
        this.scope = scope;
      };

      TopologyEditorMixin.prototype = {
        constructor: TopologyEditorMixin,
        /** Init method is called when the controller is ready. */
        init: function() {
          var self = this;
          this.scope.$on('displayUpdate', function(event, params) {
            // if the display becomes inactive then reset selection
            if(!params.displays.nodetemplate.active && _.defined(self.scope.selectedNodeTemplate)) {
              self.scope.selectedNodeTemplate = undefined;
              self.scope.$broadcast('editorSelectionChangedEvent', { nodeNames: [] });
            }
          });
        },
        /** Method triggered as a result of a on-drag (see drag and drop directive and node type search directive). */
        onDragged: function(e) {
          var nodeType = angular.fromJson(e.source);
          var evt = e.event;
          if (evt.target.hasAttribute('node-template-id')) {
            var hostNodeName = evt.target.getAttribute('node-template-id');
            this.add(nodeType, hostNodeName);
          } else {
            var targetCoord = $(evt.currentTarget).offset();
            var dropCoord = {
              x: evt.originalEvent.clientX - targetCoord.left,
              y: evt.originalEvent.clientY - targetCoord.top
            };
            this.add(nodeType, null, this.scope.graphControl.toRealCoords(dropCoord));
          }
        },
        /** this has to be exposed to the scope as we cannot rely on drag and drop callbacks for ui tests */
        add: function(nodeType, hostNodeName, dropCoord) {
          var self = this;
          var nodeTemplateName = toscaService.generateTemplateName(nodeType.elementId, this.scope.topology.topology.nodeTemplates);
          // Add node operation automatically change dependency version to higher so if different warn the user.
          var currentVersion = this.getDepVersionIfDifferent(nodeType.archiveName, nodeType.archiveVersion, this.scope.topology.topology.dependencies);
          if(_.defined(currentVersion)) {
            var modalInstance = $uibModal.open({
              templateUrl: 'views/common/confirm_modal.html',
              controller: 'ConfirmModalCtrl',
              resolve: {
                title: function() {
                  return 'APPLICATIONS.TOPOLOGY.DEPENDENCIES.VERSION_CONFLICT_TITLE';
                },
                content: function() {
                  return $translate('APPLICATIONS.TOPOLOGY.DEPENDENCIES.VERSION_CONFLICT_MSG', {
                    name: nodeType.archiveName,
                    current: currentVersion,
                    new: nodeType.archiveVersion
                  });
                }
              }
            });
            modalInstance.result.then(function () {
              self.doAddNodeTemplate(nodeTemplateName, nodeType, hostNodeName, dropCoord);
            });
          } else {
            this.doAddNodeTemplate(nodeTemplateName, nodeType, hostNodeName, dropCoord);
          }
        },
        /** Actually trigger the node template addition. */
        doAddNodeTemplate: function(nodeTemplateName, selectedNodeType, targetNodeTemplateName, dropCoord) {
          var scope = this.scope;
          // requirementSkipAutoCompletion

          // Add node operation automatically change dependency version to higher so if different warn the user.
          var hostRequirement = _.undefined(targetNodeTemplateName) ? null: nodeTemplateService.getContainerRequirement(selectedNodeType, scope.topology.relationshipTypes, scope.topology.capabilityTypes);
          scope.execute({
            type: 'org.alien4cloud.tosca.editor.operations.nodetemplate.AddNodeOperation',
            nodeName: nodeTemplateName,
            indexedNodeTypeId: selectedNodeType.id,
            coords: dropCoord,
            requirementSkipAutoCompletion: hostRequirement
          }, function(result) {
            if (_.undefined(result.error) && targetNodeTemplateName) {
              // drag a node on another node
              scope.relationships.autoOpenRelationshipModal(nodeTemplateName, targetNodeTemplateName);
            }
          }, null, nodeTemplateName);
        },
        getDepVersionIfDifferent: function(archiveName, archiveVersion, dependencies) {
          if(_.undefined(dependencies)) {
            return null;
          }
          for(var i=0; i< dependencies.length; i++) {
            if(dependencies[i].name === archiveName) {
              if(dependencies[i].version === archiveVersion) {
                return null;
              }
              return dependencies[i].version;
            }
          }
          return null;
        },
        /* Update node template name */
        updateName: function(newName) {
          var scope = this.scope;
          // Update only when the name has changed
          scope.nodeTempNameEditError = null;

          if (!newName.match(nodeNamePattern)) {
            return $filter('translate')('APPLICATIONS.TOPOLOGY.INVALID_NAME');
          }

          if (scope.selectedNodeTemplate.name !== newName) {
            scope.execute({
                type: 'org.alien4cloud.tosca.editor.operations.nodetemplate.RenameNodeOperation',
                nodeName: scope.selectedNodeTemplate.name,
                newName: newName
              }, null,
              function() { // error handling
                scope.nodeNameObj.val = scope.selectedNodeTemplate.name;
              }, scope.selectedNodeTemplate ? newName : undefined
            );
          } // if end
          scope.display.set('nodetemplate', true);
        },
        updatePosition: function(nodeName, x, y) {
          this.scope.execute({
              type: 'org.alien4cloud.tosca.editor.operations.nodetemplate.UpdateNodePositionOperation',
              nodeName: nodeName,
              coords: {
                x: Math.round(x),
                y: Math.round(y)
              }
            }, null);
        },
        delete: function(nodeTemplName) {
          var scope = this.scope;
          scope.execute({
              type: 'org.alien4cloud.tosca.editor.operations.nodetemplate.DeleteNodeOperation',
              nodeName: nodeTemplName
            },
            function(){ scope.display.displayAndUpdateVisualDimensions(['topology']); }
          );
        },

        /* Update properties of a node template */
        updateProperty: function(propertyDefinition, propertyName, propertyValue) {
          var scope = this.scope;

          var updatedNodeTemplate = scope.selectedNodeTemplate;
          return scope.execute({
              type: 'org.alien4cloud.tosca.editor.operations.nodetemplate.UpdateNodePropertyValueOperation',
              nodeName: scope.selectedNodeTemplate.name,
              propertyName: propertyName,
              propertyValue: propertyValue
            },
            function(result){
              if (_.undefined(result.error)) {
                updatedNodeTemplate.propertiesMap[propertyName].value = {value: propertyValue, definition: false};
                if (propertyName === 'component_version' || propertyName === 'version') {
                  // This is the only property with the version that updates the rendering
                  scope.$broadcast('editorUpdateNode', { node: scope.selectedNodeTemplate.name });
                }
              }
            },
            null,
            scope.selectedNodeTemplate.name,
            true
          );
        },
        /** Update the docker image of a node */
        updateDockerImage: function(dockerImage) {
          console.log('update docker image', dockerImage);
          var scope = this.scope;
          var updatedNodeTemplate = scope.selectedNodeTemplate;
          return scope.execute({
              type: 'org.alien4cloud.tosca.editor.operations.nodetemplate.UpdateDockerImageOperation',
              nodeName: scope.selectedNodeTemplate.name,
              dockerImage: dockerImage
            },
            function(result) {
              if (_.undefined(result.error)) {
                _.set(updatedNodeTemplate, 'interfaces.["tosca.interfaces.node.lifecycle.Standard"].operations.create.implementationArtifact.artifactRef', dockerImage);
                scope.dockerImage.value = dockerImage;
              }
            },
            null,
            scope.selectedNodeTemplate.name,
            true
          );
        },
        /*duplicate a node in the topology. Also duplicate the hosted nodes hierarchy. Discard any relationship targeting a node out of the hosted hierarchy*/
        duplicate: function(nodeName) {
          var scope = this.scope;
          scope.execute({
              type: 'org.alien4cloud.tosca.editor.operations.nodetemplate.DuplicateNodeOperation',
              nodeName: nodeName
            },
            null, null, nodeName
          );
        }
      };

      return function(scope) {
        var instance = new TopologyEditorMixin(scope);
        instance.init();
        scope.nodes = instance;
      };
    }
  ]); // modules
}); // define
