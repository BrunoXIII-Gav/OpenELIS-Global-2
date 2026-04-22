import React, { useEffect, useState } from "react";
import { TextInput } from "@carbon/react";

const CustomTimePicker = (props) => {
  const [currentTime, setCurrentTime] = useState(
    props.value ? props.value : "",
  );

  function handleTimePicker(e) {
    let time = e.target.value;
    setCurrentTime(time);
    props.onChange(time);
  }

  useEffect(() => {
    setCurrentTime(props.value ? props.value : "");
  }, [props.value]);

  return (
    <>
      <TextInput
        id={props.id}
        type="time"
        value={currentTime == null ? "" : currentTime}
        onChange={(e) => handleTimePicker(e)}
        labelText={props.labelText == null ? "" : props.labelText}
        invalid={props.invalid}
        invalidText={props.invalidText}
        disabled={props.disabled}
      />
    </>
  );
};

export default CustomTimePicker;
