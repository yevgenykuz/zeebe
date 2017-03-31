import {jsx, updateOnlyWhenStateChanges, withSelector,  createReferenceComponent} from 'view-utils';
import Viewer from 'bpmn-js/lib/Viewer';
import {resetZoom} from './Diagram';
import {Loader} from './LoadingIndicator';
import {createQueue, runOnce} from 'utils';

const queue = createQueue();

export const DiagramPreview = withSelector(() => {
  const Reference = createReferenceComponent();

  const template = <div style="position: relative; height: 100%; width: 100%">
    <Loader className="diagram-preview-loading" style="position: absolute" />
    <div className="diagram__holder" style="position: relative;">
      <Reference name="viewer" />
    </div>
  </div>;

  return (node, eventsBus) => {
    const templateUpdate = template(node, eventsBus);
    const viewerNode = Reference.getNode('viewer');
    const loaderNode = node.querySelector('.diagram-preview-loading');

    const viewer = new Viewer({
      container: viewerNode
    });

    const update = runOnce((diagram) => {
      queue.addTask((done) => {
        viewer.importXML(diagram, (err) => {
          loaderNode.style.display = 'none';

          if (err) {
            viewerNode.innerHTML = `Could not load diagram, got error ${err}`;
          }

          resetZoom(viewer);
          done();
        });
      });
    });

    return [templateUpdate, updateOnlyWhenStateChanges(update)];
  };
});
