package example;

import org.jmolecules.ddd.types.ValueObject;

public class SuperClassThatThrowsRTE implements ValueObject {

	public SuperClassThatThrowsRTE() {
		throw new IllegalStateException("we don't want to get this constructor being called");
	}

}
