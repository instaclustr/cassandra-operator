package controller

import (
	"fmt"
	"io"
)

type conditionalProperty struct {
	property  string
	condition func() bool
}

func writeFormattedProperties(w io.Writer, properties map[string]string) error {
	for format, value := range properties {
		if err := writeFormattedProperty(w, format, value); err != nil {
			return err
		}
	}

	return nil
}

func writeFormattedProperty(w io.Writer, format string, value string) error {
	if _, err := fmt.Fprintf(w, format, value); err != nil {
		return err
	}

	return nil
}

func writeKeyValueProperty(w io.Writer, key string, value string) error {
	_, err := fmt.Fprintf(w, "%s=%s\n", key, value)

	return err
}

func writePropertiesWithNewLine(w io.Writer, properties []string) error {
	return writeProperties(w, properties, NewLine)
}

func writeProperties(w io.Writer, properties []string, separator string) error {
	for _, property := range properties {

		var toWrite = property

		if separator != "" {
			toWrite = toWrite + separator
		}

		err := writeProperty(w, toWrite)

		if err != nil {
			return err
		}
	}

	return nil
}

func writeProperty(w io.Writer, property string) error {
	_, err := fmt.Fprint(w, property)

	return err
}

func writeKeyValueProperties(w io.Writer, keysValues map[string]string) error {
	for key, value := range keysValues {

		err := writeKeyValueProperty(w, key, value)

		if err != nil {
			return err
		}
	}

	return nil
}

func writePropertiesConditionally(w io.Writer, properties []conditionalProperty) error {

	for _, property := range properties {
		if err := writePropertyConditionally(w, property); err != nil {
			return err
		}
	}

	return nil
}

func writePropertyConditionally(w io.Writer, property conditionalProperty) error {

	if property.condition == nil {
		_, err := fmt.Fprint(w, property.property)

		if err != nil {
			return err
		}

		return nil
	}

	if property.condition() {
		_, err := fmt.Fprint(w, property.property)

		if err != nil {
			return err
		}
	}

	return nil
}
