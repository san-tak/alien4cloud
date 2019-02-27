define(function(require) {
  'use strict';

  var modules = require('modules');

  modules.get('a4c-orchestrators', ['a4c-common']).factory('locationService', ['$alresource',
    function($alresource) {
      return $alresource('rest/latest/orchestrators/:orchestratorId/locations/:locationId');
    }
  ]);

  modules.get('a4c-orchestrators', ['a4c-common']).factory('locationSecretService', ['$alresource',
    function($alresource) {
      return $alresource('rest/latest/orchestrators/:orchestratorId/locations/:locationId/secret-conf');
    }
  ]);

  modules.get('a4c-orchestrators', ['a4c-common']).factory('locationResourcesService', ['$alresource',
    function($alresource) {
      return $alresource('rest/latest/orchestrators/:orchestratorId/locations/:locationId/resources/:id/:extendUrl');
    }
  ]);

  modules.get('a4c-orchestrators', ['a4c-common']).factory('locationPoliciesService', ['$alresource',
    function($alresource) {
      return $alresource('rest/latest/orchestrators/:orchestratorId/locations/:locationId/policies/:id/:extendUrl');
    }
  ]);

  modules.get('a4c-orchestrators', ['a4c-common']).factory('locationResourcesPropertyService', ['$alresource',
    function($alresource) {
      return $alresource('rest/latest/orchestrators/:orchestratorId/locations/:locationId/resources/:id/template/properties');
    }
  ]);

  modules.get('a4c-orchestrators', ['a4c-common']).factory('locationPoliciesPropertyService', ['$alresource',
    function($alresource) {
      return $alresource('rest/latest/orchestrators/:orchestratorId/locations/:locationId/policies/:id/template/properties');
    }
  ]);

  modules.get('a4c-orchestrators', ['a4c-common']).factory('locationResourcesCapabilityPropertyService', ['$alresource',
    function($alresource) {
      return $alresource('rest/latest/orchestrators/:orchestratorId/locations/:locationId/resources/:id/template/capabilities/:capabilityName/properties');
    }
  ]);

  modules.get('a4c-orchestrators', [ 'a4c-common' ]).factory('locationResourcesPortabilityService', ['$alresource',
    function($alresource) {
      return $alresource('rest/latest/orchestrators/:orchestratorId/locations/:locationId/resources/:id/template/portability');
    }
  ]);

});
