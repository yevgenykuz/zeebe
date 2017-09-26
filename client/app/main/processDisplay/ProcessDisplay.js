import {jsx, withSelector, createStateComponent} from 'view-utils';
import {loadData, loadDiagram, getDefinitionId} from './service';
import {isViewSelected, Controls} from './controls';
import {resetStatisticData, ViewsDiagramArea, ViewsArea, getDiagramXML} from './views';

export const ProcessDisplay = withSelector(Process);

function Process() {
  return (node, eventsBus) => {
    const State = createStateComponent();
    const template = <State>
      <div className="process-display">
        <Controls selector={createControlsState} onCriteriaChanged={handleCriteriaChange} getProcessDefinition={getDefinitionId} getDiagramXML={getXML} >
          <ViewsArea areaComponent="Controls" selector="views" isViewSelected={isViewSelected} />
        </Controls>
        <ViewsDiagramArea selector="views" isViewSelected={isViewSelected} />
        <ViewsArea areaComponent="Additional" selector="views" isViewSelected={isViewSelected} />
      </div>
    </State>;

    loadDiagram();

    function handleCriteriaChange(newCriteria) {
      resetStatisticData();
      loadData(newCriteria);
    }

    function createControlsState({controls, views}) {
      return {
        ...controls,
        views
      };
    }

    function getXML() {
      const {views} = State.getState() || {};

      if (views) {
        return getDiagramXML(views);
      }
    }

    return template(node, eventsBus);
  };
}
