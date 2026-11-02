import { CustomerDetailsPage } from "../../../features/customers/components/CustomerDetailsPage";

interface CustomerDetailsRouteProps {
  params: Promise<{ customerId: string }>;
  searchParams: Promise<{ created?: string; updated?: string }>;
}

export default async function CustomerDetailsRoute({
  params,
  searchParams,
}: CustomerDetailsRouteProps) {
  const [{ customerId }, query] = await Promise.all([params, searchParams]);
  const notice =
    query.created === "1"
      ? "Customer created successfully."
      : query.updated === "1"
        ? "Customer updated successfully."
        : undefined;
  return <CustomerDetailsPage customerId={customerId} notice={notice} />;
}
