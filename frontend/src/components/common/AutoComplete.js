import React, { useState, useEffect } from "react";
import "../admin/reflexTests/ReflexStyles.css";
import { TextInput } from "@carbon/react";

function AutoComplete(props) {
  const allowFreeText = props.allowFreeText;
  const maxSuggestions =
    Number.isFinite(Number(props.maxSuggestions)) &&
    Number(props.maxSuggestions) > 0
      ? Number(props.maxSuggestions)
      : null;

  const [textValue, setTextValue] = useState("");
  const [activeSuggestion, setActiveSuggestion] = useState(0);
  const [filteredSuggestions, setFilteredSuggestions] = useState([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [userInput, setUserInput] = useState("");
  const [invalid, setInvalid] = useState(false);
  const [innitialised, setInnitialised] = useState(false);

  useEffect(() => {
    if (props.value && !innitialised) {
      if (props.suggestions) {
        var filteredSuggestion = props.suggestions.filter(
          (suggestion) =>
            suggestion.id == props.value || suggestion.id === props.value,
        );
        if (filteredSuggestion[0]) {
          setTextValue(filteredSuggestion[0].value);
        } else {
          setTextValue(props.value);
        }
      }
    }
  }, [props]);

  const buildFilteredSuggestions = (inputValue = "") => {
    const { suggestions } = props;
    const normalizedInput = String(inputValue || "");
    const allMatches =
      normalizedInput.trim() === ""
        ? suggestions
        : suggestions.filter(
            (suggestion) =>
              suggestion.value
                .toLowerCase()
                .indexOf(normalizedInput.toLowerCase()) > -1,
          );

    return maxSuggestions ? allMatches.slice(0, maxSuggestions) : allMatches;
  };

  const onChange = (e) => {
    if (props.disabled || props.readOnly) {
      return;
    }
    const { suggestions } = props;
    const userInput = e.currentTarget.value;
    setTextValue(userInput);
    const filteredSuggestions = buildFilteredSuggestions(userInput);

    setActiveSuggestion(0);
    setFilteredSuggestions(filteredSuggestions);
    setUserInput(e.currentTarget.value);
    setShowSuggestions(true);
    setInnitialised(true);

    if (filteredSuggestions.length == 0 && !allowFreeText) {
      setInvalid(true);
    }
    if (typeof props.onChange === "function") {
      props.onChange(e);
    }
  };

  const openSuggestions = () => {
    if (props.disabled || props.readOnly) {
      return;
    }

    const filtered = buildFilteredSuggestions(textValue);
    setActiveSuggestion(0);
    setFilteredSuggestions(filtered);
    setShowSuggestions(true);
    setInnitialised(true);
  };

  const onClick = (e, id, suggestion) => {
    if (props.disabled || props.readOnly) {
      return;
    }
    const { onSelect } = props;
    setTextValue(suggestion.value);
    setActiveSuggestion(0);
    setFilteredSuggestions([]);
    setUserInput(e.currentTarget.innerText);
    setShowSuggestions(false);
    setInvalid(false);

    if (typeof onSelect === "function") {
      onSelect(id);
    }
  };

  const onKeyDown = (e) => {
    if (props.disabled || props.readOnly) {
      return;
    }
    // Handeling enter key
    const { onSelect } = props;
    if (e.keyCode === 13) {
      if (filteredSuggestions[activeSuggestion]) {
        const selectedValue = filteredSuggestions[activeSuggestion].value;
        setUserInput(selectedValue);
        setTextValue(selectedValue);
        setShowSuggestions(false);
        setInvalid(false);

        if (typeof onSelect === "function") {
          onSelect(filteredSuggestions[activeSuggestion].id);
        }
      }
    }
    // Handeling up arrow
    else if (e.keyCode === 38) {
      if (activeSuggestion === 0) {
        return;
      }
      setActiveSuggestion(activeSuggestion - 1);
    }
    // Handeling down arrow
    else if (e.keyCode === 40) {
      if (activeSuggestion === filteredSuggestions.length - 1) {
        return;
      }
      setActiveSuggestion(activeSuggestion + 1);
    }
  };

  let suggestionsListComponent;
  if (showSuggestions) {
    if (filteredSuggestions.length) {
      suggestionsListComponent = (
        <div className="suggestions-container">
          <ul className="suggestions">
            {filteredSuggestions.map((suggestion, index) => {
              let className;
              // Flag the active suggestion with a class
              if (index === activeSuggestion) {
                className = "suggestion-active";
              }
              return (
                <li
                  data-cy="auto-suggestion"
                  className={className}
                  key={index}
                  onClick={(e) => onClick(e, suggestion.id, suggestion)}
                >
                  {suggestion.value}
                </li>
              );
            })}
          </ul>
        </div>
      );
    } else {
      suggestionsListComponent = (
        <div className="no-suggestions">
          <em>No suggestions available.</em>
        </div>
      );
    }
  }

  return (
    <>
      <TextInput
        type="text"
        id={props.id}
        name={props.name}
        labelText={props.label ? props.label : ""}
        className={props.class}
        autoComplete={props.autoComplete || "off"}
        data-form-type={props.dataFormType || "other"}
        spellCheck={props.spellCheck || false}
        onChange={onChange}
        onKeyDown={onKeyDown}
        onFocus={openSuggestions}
        onClick={openSuggestions}
        value={textValue}
        disabled={props.disabled}
        readOnly={props.readOnly}
        invalid={invalid}
        required={props.required ? props.required : false}
        invalidText={props.invalidText}
      />
      {suggestionsListComponent}
    </>
  );
}

export default AutoComplete;
