import { FeedbackRequestDetailsPage } from "../../../../../features/feedback/components/FeedbackRequestDetailsPage";

interface FeedbackRequestRouteProps {
  params: Promise<{ requestId: string }>;
}

export default async function FeedbackRequestRoute({
  params,
}: FeedbackRequestRouteProps) {
  const { requestId } = await params;
  return <FeedbackRequestDetailsPage requestId={requestId} />;
}
