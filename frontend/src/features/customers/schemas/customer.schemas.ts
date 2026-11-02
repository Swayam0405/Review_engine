import type {
  CustomerFieldErrors,
  CustomerInput,
} from "../types/customer.types";

const CONTROL_CHARACTERS = /\p{Cc}/u;

export function validateCustomer(input: CustomerInput): CustomerFieldErrors {
  const errors: CustomerFieldErrors = {};
  const name = input.name.trim();
  const phone = input.phone.trim();

  if (!name) {
    errors.name = "Customer name is required.";
  } else if (name.length > 120 || CONTROL_CHARACTERS.test(name)) {
    errors.name = "Enter a name of 120 characters or fewer without control characters.";
  }

  if (!phone) {
    errors.phone = "Phone number is required.";
  } else if (phone.length > 64) {
    errors.phone = "Phone number must be 64 characters or fewer.";
  }

  return errors;
}
