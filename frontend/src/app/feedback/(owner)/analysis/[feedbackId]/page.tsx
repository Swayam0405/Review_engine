import { FeedbackAnalysisPage } from "../../../../../features/feedback/components/FeedbackAnalysisPage";

interface FeedbackAnalysisRouteProps {
  params: Promise<{ feedbackId: string }>;
}

export default async function FeedbackAnalysisRoute({
  params,
}: FeedbackAnalysisRouteProps) {
  const { feedbackId } = await params;
  return <FeedbackAnalysisPage feedbackId={feedbackId} />;
}
