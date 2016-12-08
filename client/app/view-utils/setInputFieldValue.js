import {Select} from './select';
import {jsx} from './jsx';

export function setInputValue(input, value) {
  const {selectionStart, selectionEnd, selectionDirection} = input;

  input.value = value || '';
  input.selectionStart = selectionStart;
  input.selectionEnd = selectionEnd;
  input.selectionDirection = selectionDirection;
}


function InputSetter() {
  return (input) => {
    return setInputValue.bind(null, input);
  };
}

export function SetInputFieldValue({getValue}) {
  return <Select selector={getValue}>
    <InputSetter/>
  </Select>;
}
