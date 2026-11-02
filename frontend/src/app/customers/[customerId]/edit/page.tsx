import { CustomerFormPage } from "../../../../features/customers/components/CustomerFormPage";

export default async function EditCustomerRoute({
  params,
}: {
  params: Promise<{ customerId: string }>;
}) {
  const { customerId } = await params;
  return <CustomerFormPage customerId={customerId} mode="edit" />;
}
