import { TransactionDetailsPage } from "../../../features/transactions/components/TransactionDetailsPage";

export default async function TransactionDetailsRoute({
  params,
  searchParams,
}: {
  params: Promise<{ transactionId: string }>;
  searchParams: Promise<{ created?: string }>;
}) {
  const [{ transactionId }, query] = await Promise.all([params, searchParams]);
  return (
    <TransactionDetailsPage
      transactionId={transactionId}
      created={query.created === "1"}
    />
  );
}
